package lt.ktu.paskutinisatsiskaitymas.server;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.JsonMessageCodec;
import lt.ktu.paskutinisatsiskaitymas.protocol.Message;
import lt.ktu.paskutinisatsiskaitymas.protocol.ProtocolException;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/** Headless transport adapter. Connections carry handshake state, never domain entities. */
public final class GameWebSocketServer extends WebSocketServer implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(GameWebSocketServer.class.getName());
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final PlayerSlots slots = new PlayerSlots();
    private final GameRuntime runtime = new GameRuntime(this::broadcastSnapshot);
    private final MessageRouter router = new MessageRouter(runtime.commands(), slots);
    private final CompletableFuture<Void> started = new CompletableFuture<>();

    public GameWebSocketServer(InetSocketAddress address) {
        // Bound frames and reassembled messages as well as decoded JSON text.
        super(address, 2, List.of(new Draft_6455(List.of(), JsonMessageCodec.MAX_MESSAGE_CHARACTERS * 4)));
        setConnectionLostTimeout(30);
    }

    public void awaitStarted(Duration timeout) throws Exception {
        started.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        if (!"/game".equals(handshake.getResourceDescriptor())) {
            connection.close(1008, "Use /game");
            return;
        }
        ConnectionSession session = new ConnectionSession();
        connection.setAttachment(session);
        LOG.log(System.Logger.Level.INFO, "Connected {0} from {1}", session.id, connection.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket connection, String text) {
        ConnectionSession session = connection.getAttachment();
        if (session == null) {
            return;
        }
        try {
            Message response = router.route(session, codec.decode(text));
            if (response != null) {
                send(connection, response);
            }
        } catch (ProtocolException exception) {
            send(connection, new ErrorMessage("INVALID_MESSAGE", "Invalid or unsupported JSON message"));
        }
    }

    @Override
    public void onMessage(WebSocket connection, ByteBuffer bytes) {
        connection.close(1003, "Only JSON text messages are supported");
    }

    private void send(WebSocket connection, Message message) {
        try {
            if (connection.isOpen()) {
                connection.send(codec.encode(message));
            }
        } catch (ProtocolException exception) {
            LOG.log(System.Logger.Level.ERROR, "Cannot encode response", exception);
            connection.close(1011, "Cannot encode response");
        }
    }

    private void broadcastSnapshot(WorldSnapshot snapshot) {
        final String json;
        try {
            json = codec.encode(snapshot);
        } catch (ProtocolException exception) {
            LOG.log(System.Logger.Level.ERROR, "Cannot encode world snapshot", exception);
            return;
        }
        for (WebSocket connection : getConnections()) {
            ConnectionSession session = connection.getAttachment();
            if (connection.isOpen() && session != null && session.playerId != null) {
                connection.send(json);
            }
        }
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        ConnectionSession session = connection.getAttachment();
        if (session != null) {
            router.disconnected(session);
        }
        LOG.log(System.Logger.Level.INFO, "Disconnected {0}, code {1}", session == null ? "uninitialized" : session.id, code);
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {
        started.completeExceptionally(exception);
        LOG.log(System.Logger.Level.ERROR, "WebSocket error", exception);
    }

    @Override
    public void onStart() {
        LOG.log(System.Logger.Level.INFO, "Listening on {0}:{1}/game", getAddress().getHostString(), Integer.toString(getPort()));
        started.complete(null);
        runtime.start();
    }

    @Override
    public void close() throws InterruptedException {
        try {
            stop(3000, "Server shutting down");
        } finally {
            runtime.close();
        }
    }

    boolean isGameLoopTerminated() {
        return runtime.isTerminated();
    }
}
