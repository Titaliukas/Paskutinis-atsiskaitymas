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
    void multipleClientsHandshakePingAndReconnectIndependently() throws Exception {
        Peer first = connect("/game");
        Peer second = connect("/game");
        first.send(new Hello("First"));
        second.send(new Hello("Second"));
        Welcome a = assertInstanceOf(Welcome.class, first.receive());
        Welcome b = assertInstanceOf(Welcome.class, second.receive());
        assertEquals("First", a.nickname());
        assertEquals("Second", b.nickname());
        assertNotEquals(a.connectionId(), b.connectionId());
        first.send(new Ping("one"));
        second.send(new Ping("two"));
        assertEquals(new Pong("one"), first.receive());
        assertEquals(new Pong("two"), second.receive());
        first.socket.sendClose(1000, "done").get(5, TimeUnit.SECONDS);
        assertEquals(1000, first.closed.get(5, TimeUnit.SECONDS));
        second.send(new Ping("still-here"));
        assertEquals(new Pong("still-here"), second.receive());
        Peer reconnect = connect("/game");
        reconnect.send(new Hello("First"));
        assertNotEquals(a.connectionId(), assertInstanceOf(Welcome.class, reconnect.receive()).connectionId());
    }

    @Test
    void rejectsInvalidMessagesWithoutCorruptingHandshakeState() throws Exception {
        Peer peer = connect("/game");
        peer.send(new Ping("early"));
        assertEquals("HANDSHAKE_REQUIRED", assertInstanceOf(ErrorMessage.class, peer.receive()).code());
        peer.socket.sendText("not-json", true).get(5, TimeUnit.SECONDS);
        assertEquals("INVALID_MESSAGE", assertInstanceOf(ErrorMessage.class, peer.receive()).code());
        peer.send(new Hello("Player"));
        assertInstanceOf(Welcome.class, peer.receive());
        peer.send(new Hello("Replacement"));
        assertEquals("ALREADY_CONNECTED", assertInstanceOf(ErrorMessage.class, peer.receive()).code());
        peer.send(new Pong("wrong-direction"));
        assertEquals("UNEXPECTED_MESSAGE", assertInstanceOf(ErrorMessage.class, peer.receive()).code());
        peer.send(new Ping("valid"));
        assertEquals(new Pong("valid"), peer.receive());
    }

    @Test
    void reassemblesFragmentedJson() throws Exception {
        Peer peer = connect("/game");
        peer.socket.sendText("{\"type\":\"HELLO\",", false).get(5, TimeUnit.SECONDS);
        peer.socket.sendText("\"nickname\":\"Fragmented\"}", true).get(5, TimeUnit.SECONDS);
        assertEquals("Fragmented", assertInstanceOf(Welcome.class, peer.receive()).nickname());
    }

    @Test
    void closesWrongPathAndBinaryMessages() throws Exception {
        Peer wrongPath = connect("/other");
        assertEquals(1008, wrongPath.closed.get(5, TimeUnit.SECONDS));
        Peer binary = connect("/game");
        binary.socket.sendBinary(ByteBuffer.wrap(new byte[]{1}), true).get(5, TimeUnit.SECONDS);
        assertEquals(1003, binary.closed.get(5, TimeUnit.SECONDS));
    }

    @Test
    void serverShutdownClosesConnectedClients() throws Exception {
        Peer peer = connect("/game");
        peer.send(new Hello("Player"));
        assertInstanceOf(Welcome.class, peer.receive());
        server.close();
        assertEquals(1001, peer.closed.get(5, TimeUnit.SECONDS));
    }

    private Peer connect(String path) throws Exception {
        Peer peer = new Peer();
        peers.add(peer);
        peer.socket = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://127.0.0.1:" + server.getPort() + path), peer).get(5, TimeUnit.SECONDS);
        return peer;
    }

    private final class Peer implements WebSocket.Listener {
        private final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final StringBuilder fragments = new StringBuilder();
        private WebSocket socket;

        void send(Message message) throws Exception {
            socket.sendText(codec.encode(message), true).get(5, TimeUnit.SECONDS);
        }

        Message receive() throws Exception {
            String json = inbox.poll(5, TimeUnit.SECONDS);
            assertNotNull(json, "Timed out waiting for server response");
            return codec.decode(json);
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
