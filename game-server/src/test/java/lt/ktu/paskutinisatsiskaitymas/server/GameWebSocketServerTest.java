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
        assertEquals(2, initial.npcs().size());
        assertTrue(initial.npcs().stream().allMatch(npc -> List.of("PATROL", "CHASE", "ATTACK", "FLEE")
                .contains(npc.activity())));
        WorldSnapshot secondView = second.receive(WorldSnapshot.class, value -> value.npcs().size() == 2);
        assertEquals(initial.npcs().stream().map(NPCSnapshot::id).toList(),
                secondView.npcs().stream().map(NPCSnapshot::id).toList());
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
    void fourPlayersReceiveUniqueSlotsAndMoveIndependently() throws Exception {
        List<Peer> players = new ArrayList<>();
        List<Welcome> welcomes = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Peer peer = connect("/game");
            peer.send(new Hello("Player " + index));
            players.add(peer);
            welcomes.add(peer.receive(Welcome.class, value -> true));
        }
        assertEquals(4, welcomes.stream().map(Welcome::playerId).distinct().count());
        assertEquals(List.of(0, 1, 2, 3), welcomes.stream().map(Welcome::slot).sorted().toList());
        Peer observer = players.getFirst();
        for (Peer peer : players) {
            WorldSnapshot view = peer.receive(WorldSnapshot.class, value -> value.players().size() == 4);
            assertEquals(4, view.players().stream().map(PlayerSnapshot::playerId).distinct().count());
            assertEquals(2, view.npcs().size());
        }
        for (int index = 0; index < 4; index++) {
            Welcome active = welcomes.get(index);
            WorldSnapshot before = observer.receive(WorldSnapshot.class,
                    value -> value.players().size() == 4 && value.players().stream().allMatch(p -> p.velocityX() == 0));
            players.get(index).send(new InputState(0, false, true, false));
            WorldSnapshot moved = observer.receive(WorldSnapshot.class,
                    value -> value.tick() > before.tick() && value.players().size() == 4
                            && player(value, active).x() > player(before, active).x());
            for (Welcome other : welcomes) {
                if (!other.playerId().equals(active.playerId())) {
                    assertEquals(player(before, other).x(), player(moved, other).x(), 0.000_001);
                }
            }
            players.get(index).send(new InputState(1, false, false, false));
            observer.receive(WorldSnapshot.class,
                    value -> value.tick() > moved.tick() && player(value, active).velocityX() == 0);
        }
    }

    @Test
    void rejectsFifthPlayerThenReusesDisconnectedSlotWithFreshIdentity() throws Exception {
        List<Peer> players = new ArrayList<>();
        List<Welcome> welcomes = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Peer peer = connect("/game");
            peer.send(new Hello("Player " + index));
            players.add(peer);
            welcomes.add(peer.receive(Welcome.class, value -> true));
        }
        WorldSnapshot before = players.getFirst().receive(WorldSnapshot.class, value -> value.players().size() == 4);
        Peer fifth = connect("/game");
        fifth.send(new Hello("Fifth"));
        assertEquals("SERVER_FULL", fifth.receive(ErrorMessage.class, value -> value.code().equals("SERVER_FULL")).code());

        Welcome departed = welcomes.get(2);
        players.get(2).socket.sendClose(1000, "done").get(5, TimeUnit.SECONDS);
        assertEquals(1000, players.get(2).closed.get(5, TimeUnit.SECONDS));
        WorldSnapshot afterLeave = players.getFirst().receive(WorldSnapshot.class,
                value -> value.players().size() == 3 && value.players().stream()
                        .noneMatch(p -> p.playerId().equals(departed.playerId())));
        for (int index : new int[]{0, 1, 3}) {
            Welcome survivor = welcomes.get(index);
            assertEquals(player(before, survivor).x(), player(afterLeave, survivor).x(), 0.000_001);
            players.get(index).send(new Ping("alive-" + index));
            assertEquals(new Pong("alive-" + index), players.get(index).receive(Pong.class, value -> true));
        }
        fifth.send(new Hello("Replacement"));
        Welcome replacement = fifth.receive(Welcome.class, value -> value.nickname().equals("Replacement"));
        assertEquals(departed.slot(), replacement.slot());
        assertNotEquals(departed.playerId(), replacement.playerId());
        WorldSnapshot afterJoin = players.getFirst().receive(WorldSnapshot.class,
                value -> value.players().size() == 4 && value.players().stream()
                        .anyMatch(p -> p.playerId().equals(replacement.playerId())));
        assertEquals(4, afterJoin.players().stream().map(PlayerSnapshot::slot).distinct().count());
        assertTrue(afterJoin.players().stream().noneMatch(p -> p.playerId().equals(departed.playerId())));
        Peer sixth = connect("/game");
        sixth.send(new Hello("Sixth"));
        assertEquals("SERVER_FULL", sixth.receive(ErrorMessage.class, value -> true).code());
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
        peer.send(new WorldSnapshot(0, new ArenaSnapshot(1, 1, List.of()), List.of(), List.of(), List.of()));
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
