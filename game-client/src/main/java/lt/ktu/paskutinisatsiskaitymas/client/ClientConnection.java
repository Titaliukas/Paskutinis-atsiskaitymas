package lt.ktu.paskutinisatsiskaitymas.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import lt.ktu.paskutinisatsiskaitymas.protocol.JsonMessageCodec;
import lt.ktu.paskutinisatsiskaitymas.protocol.ProtocolException;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;

/** Asynchronous network adapter. Never blocks Swing, renders, or owns authoritative game state. */
public final class ClientConnection implements AutoCloseable {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final ClientMessageRouter router = new ClientMessageRouter();
    private final Consumer<ConnectionStatus> observer;
    private CompletableFuture<WebSocket> pending;
    private CompletableFuture<Welcome> handshake;
    private WebSocket socket;
    private long generation;
    private boolean closed;

    public ClientConnection(Consumer<ConnectionStatus> observer) {
        this.observer = java.util.Objects.requireNonNull(observer);
    }

    public synchronized void connect(URI address, String nickname) {
        if (closed) {
            throw new IllegalStateException("Client has closed");
        }
        if (address == null || !("ws".equals(address.getScheme()) || "wss".equals(address.getScheme()))
                || address.getHost() == null || address.getFragment() != null || address.getUserInfo() != null) {
            throw new IllegalArgumentException("Use a WebSocket address such as ws://localhost:8080/game");
        }
        final String hello;
        try {
            hello = codec.encode(new Hello(nickname));
        } catch (ProtocolException exception) {
            throw new IllegalArgumentException("Cannot encode HELLO", exception);
        }
        disconnect();
        long attempt = generation;
        emit(ConnectionStatus.State.CONNECTING, "Connecting to " + address);
        handshake = new CompletableFuture<>();
        handshake.orTimeout(8, TimeUnit.SECONDS).whenComplete((welcome, error) -> {
            if (error != null) {
                fail(attempt, "Handshake failed or timed out");
            }
        });
        pending = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(address, new Listener(attempt, hello));
        pending.whenComplete((connected, error) -> {
            if (error != null) {
                fail(attempt, "Connection failed: " + error.getClass().getSimpleName());
            }
        });
    }

    public synchronized void disconnect() {
        generation++; // Ignore all late callbacks from the previous connection attempt.
        if (handshake != null) {
            handshake.cancel(false);
            handshake = null;
        }
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        if (socket != null) {
            WebSocket previous = socket;
            socket = null;
            previous.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnecting")
                    .orTimeout(2, TimeUnit.SECONDS).whenComplete((ignored, error) -> previous.abort());
        }
        emit(ConnectionStatus.State.DISCONNECTED, "Disconnected");
    }

    private synchronized void fail(long attempt, String detail) {
        if (attempt == generation) {
            disconnect();
            emit(ConnectionStatus.State.ERROR, detail);
        }
    }

    private void emit(ConnectionStatus.State state, String detail) {
        observer.accept(new ConnectionStatus(state, detail));
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            disconnect();
            http.shutdownNow();
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final long attempt;
        private final String hello;
        private final StringBuilder fragments = new StringBuilder();

        private Listener(long attempt, String hello) {
            this.attempt = attempt;
            this.hello = hello;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            synchronized (ClientConnection.this) {
                if (attempt != generation) {
                    webSocket.abort();
                    return;
                }
                socket = webSocket;
                webSocket.sendText(hello, true).whenComplete((ignored, error) -> {
                    if (error != null) {
                        fail(attempt, "Failed to send HELLO");
                    }
                });
                webSocket.request(1);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (ClientConnection.this) {
                if (attempt != generation) {
                    return CompletableFuture.completedFuture(null);
                }
                if (fragments.length() + data.length() > JsonMessageCodec.MAX_MESSAGE_CHARACTERS) {
                    fail(attempt, "Server message is too large");
                    return CompletableFuture.completedFuture(null);
                }
                fragments.append(data);
                if (last) {
                    try {
                        router.route(codec.decode(fragments.toString()), welcome -> {
                            if (handshake.complete(welcome)) {
                                emit(ConnectionStatus.State.CONNECTED, "Connected as " + welcome.nickname());
                            } else {
                                fail(attempt, "Unexpected duplicate WELCOME");
                            }
                        }, error -> fail(attempt, error.code() + ": " + error.message()));
                    } catch (ProtocolException exception) {
                        fail(attempt, "Invalid server message");
                    } finally {
                        fragments.setLength(0);
                    }
                }
                webSocket.request(1);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            fail(attempt, "Expected JSON text from server");
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            synchronized (ClientConnection.this) {
                if (attempt == generation) {
                    disconnect();
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail(attempt, "Connection lost");
        }
    }
}
