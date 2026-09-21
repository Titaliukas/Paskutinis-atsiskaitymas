package lt.ktu.paskutinisatsiskaitymas.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import lt.ktu.paskutinisatsiskaitymas.protocol.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GameWebSocketServerTest {
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final List<Peer> peers = new ArrayList<>();
    private GameWebSocketServer server;
    private HttpClient http;

    @BeforeEach
    void start() throws Exception {
        server = new GameWebSocketServer(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        server.awaitStarted(Duration.ofSeconds(5));
        http = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() throws Exception {
        for (Peer peer : peers) {
            if (peer.socket != null) {
                peer.socket.abort();
            }
        }
        if (http != null) {
            http.shutdownNow();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void twoPlayersReceiveSnapshotsAndMoveIndependently() throws Exception {
        Peer first = connect("/game");
        Peer second = connect("/game");
        first.send(new Hello("First"));
        second.send(new Hello("Second"));
        Welcome a = first.receive(Welcome.class, value -> value.nickname().equals("First"));
        Welcome b = second.receive(Welcome.class, value -> value.nickname().equals("Second"));
        assertNotEquals(a.playerId(), b.playerId());
        assertNotEquals(a.slot(), b.slot());

        WorldSnapshot initial = first.receive(WorldSnapshot.class, value -> value.players().size() == 2);
        double firstX = player(initial, a).x();
        double secondX = player(initial, b).x();
        first.send(new InputState(0, false, true, false));
        WorldSnapshot moved = first.receive(WorldSnapshot.class,
                value -> value.players().size() == 2 && player(value, a).x() > firstX);
        assertEquals(secondX, player(moved, b).x(), 0.000_001);

        first.send(new InputState(1, false, false, false));
        first.send(new Ping("one"));
        assertEquals(new Pong("one"), first.receive(Pong.class, value -> value.requestId().equals("one")));
    }

    @Test
    void rejectsThirdPlayerThenAllowsReconnectAfterDisconnect() throws Exception {
        Peer first = joined("First");
        Peer second = joined("Second");
        assertNotNull(second);
        Peer third = connect("/game");
        third.send(new Hello("Third"));
        assertEquals("SERVER_FULL", third.receive(ErrorMessage.class,
                value -> value.code().equals("SERVER_FULL")).code());

        first.socket.sendClose(1000, "done").get(5, TimeUnit.SECONDS);
        assertEquals(1000, first.closed.get(5, TimeUnit.SECONDS));
        Peer reconnect = connect("/game");
        reconnect.send(new Hello("Replacement"));
        assertEquals("Replacement", reconnect.receive(Welcome.class,
                value -> value.nickname().equals("Replacement")).nickname());
    }

    @Test
    void validatesDirectionAndMalformedMessagesWithoutLosingConnection() throws Exception {
        Peer peer = connect("/game");
        peer.send(new InputState(0, false, true, false));
        assertEquals("HANDSHAKE_REQUIRED", peer.receive(ErrorMessage.class,
                value -> value.code().equals("HANDSHAKE_REQUIRED")).code());
        peer.socket.sendText("not-json", true).get(5, TimeUnit.SECONDS);
        assertEquals("INVALID_MESSAGE", peer.receive(ErrorMessage.class,
                value -> value.code().equals("INVALID_MESSAGE")).code());
        peer.send(new Hello("Player"));
        peer.receive(Welcome.class, value -> true);
        peer.send(new Hello("Replacement"));
        assertEquals("ALREADY_CONNECTED", peer.receive(ErrorMessage.class,
                value -> value.code().equals("ALREADY_CONNECTED")).code());
        peer.send(new WorldSnapshot(0, new ArenaSnapshot(1, 1, List.of()), List.of()));
        assertEquals("UNEXPECTED_MESSAGE", peer.receive(ErrorMessage.class,
                value -> value.code().equals("UNEXPECTED_MESSAGE")).code());
    }

    @Test
    void reassemblesFragmentedJoinAndRejectsWrongPathAndBinary() throws Exception {
        Peer fragmented = connect("/game");
        fragmented.socket.sendText("{\"type\":\"HELLO\",", false).get(5, TimeUnit.SECONDS);
        fragmented.socket.sendText("\"nickname\":\"Fragmented\"}", true).get(5, TimeUnit.SECONDS);
        assertEquals("Fragmented", fragmented.receive(Welcome.class, value -> true).nickname());

        Peer wrongPath = connect("/other");
        assertEquals(1008, wrongPath.closed.get(5, TimeUnit.SECONDS));
        Peer binary = connect("/game");
        binary.socket.sendBinary(ByteBuffer.wrap(new byte[]{1}), true).get(5, TimeUnit.SECONDS);
        assertEquals(1003, binary.closed.get(5, TimeUnit.SECONDS));
    }

    @Test
    void cleanShutdownClosesClientsAndTerminatesGameLoop() throws Exception {
        Peer peer = joined("Player");
        server.close();
        assertEquals(1001, peer.closed.get(5, TimeUnit.SECONDS));
        assertTrue(server.isGameLoopTerminated());
        server = null;
        assertTrue(Thread.getAllStackTraces().keySet().stream()
                .noneMatch(thread -> thread.isAlive() && thread.getName().equals("authoritative-game-loop")));
    }

    private Peer joined(String nickname) throws Exception {
        Peer peer = connect("/game");
        peer.send(new Hello(nickname));
        peer.receive(Welcome.class, value -> value.nickname().equals(nickname));
        return peer;
    }

    private Peer connect(String path) throws Exception {
        Peer peer = new Peer();
        peers.add(peer);
        peer.socket = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://127.0.0.1:" + server.getPort() + path), peer).get(5, TimeUnit.SECONDS);
        return peer;
    }

    private static PlayerSnapshot player(WorldSnapshot snapshot, Welcome welcome) {
        return snapshot.players().stream()
                .filter(player -> player.playerId().equals(welcome.playerId()))
                .findFirst().orElseThrow();
    }

    private final class Peer implements WebSocket.Listener {
        private final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final StringBuilder fragments = new StringBuilder();
        private WebSocket socket;

        void send(Message message) throws Exception {
            socket.sendText(codec.encode(message), true).get(5, TimeUnit.SECONDS);
        }

        <T extends Message> T receive(Class<T> type, Predicate<T> predicate) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                String json = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (json == null) {
                    continue;
                }
                Message message = codec.decode(json);
                if (type.isInstance(message)) {
                    T value = type.cast(message);
                    if (predicate.test(value)) {
                        return value;
                    }
                }
            }
            fail("Timed out waiting for " + type.getSimpleName());
            throw new AssertionError();
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            fragments.append(data);
            if (last) {
                inbox.add(fragments.toString());
                fragments.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
            closed.complete(code);
            return CompletableFuture.completedFuture(null);
        }
    }
}
