package lt.ktu.paskutinisatsiskaitymas.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import lt.ktu.paskutinisatsiskaitymas.protocol.CreateLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.InputState;
import lt.ktu.paskutinisatsiskaitymas.protocol.JoinLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.JsonMessageCodec;
import lt.ktu.paskutinisatsiskaitymas.protocol.LeaveLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.ListLobbies;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyClosed;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyList;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyMember;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyState;
import lt.ktu.paskutinisatsiskaitymas.protocol.Message;
import lt.ktu.paskutinisatsiskaitymas.protocol.ProtocolException;
import lt.ktu.paskutinisatsiskaitymas.protocol.SelectCharacter;
import lt.ktu.paskutinisatsiskaitymas.protocol.StartGame;
import lt.ktu.paskutinisatsiskaitymas.protocol.StatAllocation;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** Asynchronous connection shared by the room browser, lobby and game. */
public final class ClientConnection implements AutoCloseable {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final ClientMessageRouter router = new ClientMessageRouter();
    private final List<ClientEvents> observers = new CopyOnWriteArrayList<>();
    private CompletableFuture<WebSocket> pendingConnection;
    private CompletableFuture<WebSocket> lastSend;
    private CompletableFuture<Void> requestDeadline;
    private Message pendingRequest;
    private WebSocket socket;
    private Welcome admission;
    private ConnectionStatus.State phase = ConnectionStatus.State.DISCONNECTED;
    private long generation;
    private long inputSequence;
    private boolean closed;

    public ClientConnection(ClientEvents observer) {
        addObserver(observer);
    }

    public void addObserver(ClientEvents observer) {
        observers.add(Objects.requireNonNull(observer));
    }

    public void removeObserver(ClientEvents observer) {
        observers.remove(observer);
    }

    public synchronized void connect(URI address) {
        if (closed) {
            throw new IllegalStateException("Client has closed");
        }
        if (address == null || !("ws".equals(address.getScheme()) || "wss".equals(address.getScheme()))
                || address.getHost() == null || address.getFragment() != null || address.getUserInfo() != null) {
            throw new IllegalArgumentException("Use a WebSocket address such as ws://localhost:8080/game");
        }
        if (pendingConnection != null || socket != null) {
            disconnect();
        }
        long attempt = generation;
        emit(ConnectionStatus.State.CONNECTING, "Connecting to " + address);
        pendingConnection = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(address, new Listener(attempt));
        pendingConnection.whenComplete((connected, error) -> {
            if (error != null) {
                fail(attempt, "Connection failed: " + error.getClass().getSimpleName());
            }
        });
    }

    public synchronized void listLobbies(String afterCode) {
        if (phase == ConnectionStatus.State.BROWSING) {
            request(new ListLobbies(afterCode));
        }
    }

    public synchronized void createLobby(String nickname) {
        if (phase == ConnectionStatus.State.BROWSING) {
            request(new CreateLobby(nickname));
        }
    }

    public synchronized void joinLobby(String nickname, String code) {
        if (phase == ConnectionStatus.State.BROWSING) {
            request(new JoinLobby(code, nickname));
        }
    }

    public synchronized void selectCharacter(String character, StatAllocation stats) {
        if (phase == ConnectionStatus.State.SELECTING) {
            request(new SelectCharacter(character, stats));
        }
    }

    public synchronized void startGame() {
        if (phase == ConnectionStatus.State.LOBBY) {
            request(new StartGame());
        }
    }

    public synchronized void leaveLobby() {
        if (admission != null && phase != ConnectionStatus.State.BROWSING) {
            request(new LeaveLobby());
        }
    }

    private void request(Message message) {
        if (socket == null || pendingRequest != null) {
            return;
        }
        pendingRequest = message;
        long attempt = generation;
        CompletableFuture<Void> deadline = new CompletableFuture<>();
        requestDeadline = deadline;
        deadline.orTimeout(8, TimeUnit.SECONDS).whenComplete((ignored, error) -> {
            synchronized (ClientConnection.this) {
                if (error != null && requestDeadline == deadline) {
                    fail(attempt, "The server did not respond");
                }
            }
        });
        observers.forEach(o -> o.onRequestPending(true));
        send(message);
    }

    private void finishRequest() {
        if (pendingRequest != null) {
            pendingRequest = null;
            requestDeadline.complete(null);
            requestDeadline = null;
            observers.forEach(o -> o.onRequestPending(false));
        }
    }

    private void send(Message message) {
        String json;
        try {
            json = codec.encode(message);
        } catch (ProtocolException exception) {
            fail(generation, "Cannot encode client message");
            return;
        }
        long attempt = generation;
        WebSocket target = socket;
        lastSend = lastSend.thenCompose(ignored -> target.sendText(json, true));
        lastSend.whenComplete((ignored, error) -> {
            if (error != null) {
                fail(attempt, "Failed to send client message");
            }
        });
    }

    void sendInput(PlayerInput input) {
        sendInput(input.left(), input.right(), input.jump(), input.ranged(), input.melee());
    }

    public synchronized void sendInput(boolean left, boolean right, boolean jump, boolean ranged, boolean melee) {
        if (phase == ConnectionStatus.State.CONNECTED && socket != null && pendingRequest == null) {
            send(new InputState(inputSequence++, left, right, jump, ranged, melee));
        }
    }

    public synchronized void disconnect() {
        resetConnection();
        emit(ConnectionStatus.State.DISCONNECTED, "Disconnected");
    }

    private void resetConnection() {
        generation++;
        finishRequest();
        admission = null;
        if (pendingConnection != null) {
            pendingConnection.cancel(true);
            pendingConnection = null;
        }
        if (socket != null) {
            WebSocket previous = socket;
            socket = null;
            previous.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnecting")
                    .orTimeout(2, TimeUnit.SECONDS).whenComplete((ignored, error) -> previous.abort());
        }
        lastSend = null;
    }

    private synchronized void fail(long attempt, String detail) {
        if (attempt == generation) {
            resetConnection();
            emit(ConnectionStatus.State.ERROR, detail);
        }
    }

    private void emit(ConnectionStatus.State state, String detail) {
        phase = state;
        ConnectionStatus status = new ConnectionStatus(state, detail);
        observers.forEach(o -> o.onConnectionStatus(status));
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            disconnect();
            http.shutdownNow();
        }
    }

    private final ClientEvents incoming = new ClientEvents() {
        @Override
        public void onLobbyList(LobbyList page) {
            if (!(pendingRequest instanceof ListLobbies)) {
                return;
            }
            if (phase == ConnectionStatus.State.CONNECTING) {
                emit(ConnectionStatus.State.BROWSING, "Connected to server");
            }
            finishRequest();
            observers.forEach(o -> o.onLobbyList(page));
        }

        @Override
        public void onJoined(Welcome welcome) {
            if (admission != null || !(pendingRequest instanceof CreateLobby || pendingRequest instanceof JoinLobby)) {
                fail(generation, "Unexpected room admission");
                return;
            }
            admission = welcome;
            inputSequence = 0;
            observers.forEach(o -> o.onJoined(welcome));
        }

        @Override
        public void onLobbyState(LobbyState state) {
            if (admission == null) {
                fail(generation, "Lobby state arrived before admission");
                return;
            }
            LobbyMember local = state.members().stream().filter(member -> member.playerId().equals(admission.playerId()))
                    .findFirst().orElse(null);
            if (local == null) {
                fail(generation, "Player is missing from the room");
                return;
            }
            ConnectionStatus.State next = state.phase() == LobbyState.Phase.RUNNING ? ConnectionStatus.State.CONNECTED
                    : local.character() == null ? ConnectionStatus.State.SELECTING : ConnectionStatus.State.LOBBY;
            observers.forEach(o -> o.onLobbyState(state));
            if (phase != next) {
                String detail = switch (next) {
                    case CONNECTED -> "Playing in lobby " + state.code();
                    case SELECTING -> "Choose your character";
                    default -> "Waiting in lobby " + state.code();
                };
                emit(next, detail);
            }
            if (pendingRequest instanceof CreateLobby || pendingRequest instanceof JoinLobby
                    || pendingRequest instanceof SelectCharacter && local.character() != null
                    || pendingRequest instanceof StartGame && next == ConnectionStatus.State.CONNECTED) {
                finishRequest();
            }
        }

        @Override
        public void onLobbyClosed(LobbyClosed closure) {
            admission = null;
            finishRequest();
            observers.forEach(o -> o.onLobbyClosed(closure));
            emit(ConnectionStatus.State.BROWSING, closure.reason());
            request(new ListLobbies(null));
        }

        @Override
        public void onRequestError(ErrorMessage error) {
            if (pendingRequest instanceof ListLobbies && error.code().equals("HANDSHAKE_REQUIRED")) {
                // A room action may arrive after an unsolicited closure and before the new room list.
                return;
            }
            if (phase == ConnectionStatus.State.CONNECTING) {
                fail(generation, error.message());
                return;
            }
            boolean refresh = pendingRequest instanceof JoinLobby;
            if (pendingRequest instanceof LeaveLobby && error.code().equals("SERVER_BUSY")) {
                observers.forEach(o -> o.onRequestError(error));
                return;
            }
            finishRequest();
            observers.forEach(o -> o.onRequestError(error));
            if (refresh) {
                request(new ListLobbies(null));
            }
        }

        @Override
        public void onSnapshot(WorldSnapshot snapshot) {
            if (phase == ConnectionStatus.State.CONNECTED) {
                observers.forEach(o -> o.onSnapshot(snapshot));
            }
        }

        @Override
        public void onGameEvent(GameEventMessage event) {
            if (phase == ConnectionStatus.State.CONNECTED) {
                observers.forEach(o -> o.onGameEvent(event));
            }
        }
    };

    private final class Listener implements WebSocket.Listener {
        private final long attempt;
        private final StringBuilder fragments = new StringBuilder();

        private Listener(long attempt) {
            this.attempt = attempt;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            synchronized (ClientConnection.this) {
                if (attempt != generation) {
                    webSocket.abort();
                    return;
                }
                socket = webSocket;
                lastSend = CompletableFuture.completedFuture(webSocket);
                request(new ListLobbies(null));
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
                        router.route(codec.decode(fragments.toString()), incoming);
                    } catch (ProtocolException exception) {
                        fail(attempt, "Invalid server message");
                    } finally {
                        fragments.setLength(0);
                    }
                }
                if (attempt == generation) {
                    webSocket.request(1);
                }
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
                    resetConnection();
                    emit(ConnectionStatus.State.DISCONNECTED, "Connection closed");
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
