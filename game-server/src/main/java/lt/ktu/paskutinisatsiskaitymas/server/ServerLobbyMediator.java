package lt.ktu.paskutinisatsiskaitymas.server;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lt.ktu.paskutinisatsiskaitymas.application.InputCommand;
import lt.ktu.paskutinisatsiskaitymas.application.JoinPlayerCommand;
import lt.ktu.paskutinisatsiskaitymas.application.LeavePlayerCommand;
import lt.ktu.paskutinisatsiskaitymas.domain.Arena;
import lt.ktu.paskutinisatsiskaitymas.domain.CharacterBuild;
import lt.ktu.paskutinisatsiskaitymas.domain.CharacterType;
import lt.ktu.paskutinisatsiskaitymas.protocol.CreateLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.InputState;
import lt.ktu.paskutinisatsiskaitymas.protocol.JoinLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyClosed;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyMember;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyState;
import lt.ktu.paskutinisatsiskaitymas.protocol.Message;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;

/**
 * Concrete mediator: serializes membership and lifecycle decisions; each world has its own owner loop.
 * Outbound sends enqueue transport writes only. No worker is awaited while holding this monitor.
 */
final class ServerLobbyMediator implements LobbyMediator {
    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Lobby> lobbies = new LinkedHashMap<>();
    private final Arena arena;
    private final ExecutorService cleanup = Executors.newSingleThreadExecutor(runnable ->
            new Thread(runnable, "lobby-cleanup"));
    private boolean closed;

    ServerLobbyMediator(Arena arena) {
        this.arena = java.util.Objects.requireNonNull(arena, "arena");
    }

    @Override
    public synchronized void create(ConnectionSession participant, CreateLobby request) {
        if (!canEnter(participant)) {
            return;
        }
        CharacterBuild build = validateBuild(participant, request);
        if (build == null) {
            return;
        }
        String code;
        do {
            StringBuilder candidate = new StringBuilder();
            for (int index = 0; index < 6; index++) {
                candidate.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            code = candidate.toString();
        } while (lobbies.containsKey(code));
        Lobby lobby = new Lobby(code, participant.id);
        lobbies.put(code, lobby);
        admit(lobby, participant, request, build);
    }

    @Override
    public synchronized void join(ConnectionSession participant, JoinLobby request) {
        if (!canEnter(participant)) {
            return;
        }
        CreateLobby selection = new CreateLobby(request.nickname(), request.character(), request.stats());
        CharacterBuild build = validateBuild(participant, selection);
        if (build == null) {
            return;
        }
        Lobby lobby = lobbies.get(request.code());
        if (lobby == null) {
            error(participant, "LOBBY_NOT_FOUND", "No waiting lobby has that code");
        } else if (lobby.running) {
            error(participant, "LOBBY_STARTED", "This game has started and no longer accepts players");
        } else {
            admit(lobby, participant, selection, build);
        }
    }

    private boolean canEnter(ConnectionSession participant) {
        if (closed || participant.disconnected) {
            return false;
        }
        if (participant.lobbyCode != null) {
            error(participant, "ALREADY_CONNECTED", "Leave your current lobby first");
            return false;
        }
        return true;
    }

    private CharacterBuild validateBuild(ConnectionSession participant, CreateLobby selection) {
        try {
            return new CharacterBuild(selection.stats().healthPercent(), selection.stats().speedPercent(),
                    selection.stats().jumpHeightPercent());
        } catch (IllegalArgumentException exception) {
            error(participant, "INVALID_STATS", exception.getMessage());
            return null;
        }
    }

    private void admit(Lobby lobby, ConnectionSession participant, CreateLobby selection, CharacterBuild build) {
        PlayerSlots.Assignment assignment = lobby.slots.reserve(participant.id).orElse(null);
        if (assignment == null) {
            error(participant, "LOBBY_FULL", "The four lobby slots are occupied");
            return;
        }
        participant.playerId = assignment.playerId();
        participant.slot = assignment.slot();
        participant.nickname = selection.nickname();
        participant.lobbyCode = lobby.code;
        JoinPlayerCommand command = new JoinPlayerCommand(assignment.playerId(), assignment.slot(),
                selection.nickname(), CharacterType.valueOf(selection.character()), build);
        lobby.members.put(participant.id, new Member(participant, selection.character(), command));
        participant.send(new Welcome(participant.id, participant.playerId, participant.slot, participant.nickname));
        publishState(lobby);
    }

    @Override
    public synchronized void start(ConnectionSession participant) {
        Lobby lobby = membership(participant);
        if (lobby == null) {
            return;
        }
        if (!lobby.hostConnectionId.equals(participant.id)) {
            error(participant, "HOST_REQUIRED", "Only the host can start the game");
        } else if (lobby.running) {
            error(participant, "LOBBY_STARTED", "The game has already started");
        } else {
            lobby.runtime = new GameRuntime(snapshot -> publishGame(lobby, snapshot),
                    event -> publishGame(lobby, event), arena);
            for (Member member : lobby.members.values()) {
                if (!lobby.runtime.commands().offer(member.join())) {
                    throw new IllegalStateException("Fresh game queue cannot hold lobby members");
                }
            }
            lobby.running = true;
            // Order RUNNING before any world snapshots or events on every connection.
            publishState(lobby);
            lobby.runtime.start();
        }
    }

    @Override
    public synchronized void input(ConnectionSession participant, InputState input) {
        Lobby lobby = membership(participant);
        if (lobby == null) {
            return;
        }
        if (!lobby.running) {
            error(participant, "GAME_NOT_STARTED", "Wait for the host to start the game");
        } else if (!lobby.runtime.commands().offer(new InputCommand(participant.playerId, input.sequence(),
                input.left(), input.right(), input.jump(), input.ranged(), input.melee(), input.examinator(), input.prone()))) {
            error(participant, "SERVER_BUSY", "The command queue is full");
        }
    }

    private Lobby membership(ConnectionSession participant) {
        if (closed || participant.disconnected) {
            return null;
        }
        Lobby lobby = lobbies.get(participant.lobbyCode);
        if (lobby == null) {
            error(participant, "HANDSHAKE_REQUIRED", "Create or join a lobby first");
        }
        return lobby;
    }

    private void publishState(Lobby lobby) {
        UUID hostId = lobby.members.get(lobby.hostConnectionId).participant().playerId;
        LobbyState state = new LobbyState(lobby.code, hostId,
                lobby.running ? LobbyState.Phase.RUNNING : LobbyState.Phase.WAITING,
                lobby.members.values().stream().map(member -> new LobbyMember(member.participant().playerId,
                        member.participant().slot, member.participant().nickname, member.character())).toList());
        lobby.members.values().forEach(member -> member.participant().send(state));
    }

    private synchronized void publishGame(Lobby lobby, Message message) {
        if (!closed && lobbies.get(lobby.code) == lobby && lobby.running) {
            lobby.members.values().forEach(member -> member.participant().send(message));
        }
    }

    @Override
    public synchronized void disconnected(ConnectionSession participant) {
        participant.disconnected = true;
        Lobby lobby = lobbies.get(participant.lobbyCode);
        if (lobby == null) {
            participant.clearMembership();
            return;
        }
        if (lobby.hostConnectionId.equals(participant.id)) {
            closeLobby(lobby, "The host left. The lobby has closed.");
        } else {
            if (lobby.running) {
                // At most three guest departures can occur after start: lifecycle work fits the bounded queue.
                boolean queued = lobby.runtime.commands().offerAfterEvicting(new LeavePlayerCommand(participant.playerId),
                        command -> command instanceof InputCommand);
                if (!queued) {
                    closeLobby(lobby, "The game could not process a departure. Please create a new lobby.");
                    return;
                }
            }
            lobby.members.remove(participant.id);
            lobby.slots.release(participant.id);
            participant.clearMembership();
            publishState(lobby);
        }
    }

    private void closeLobby(Lobby lobby, String reason) {
        lobbies.remove(lobby.code);
        for (Member member : lobby.members.values()) {
            member.participant().send(new LobbyClosed(reason));
            lobby.slots.release(member.participant().id);
            member.participant().clearMembership();
        }
        lobby.members.clear();
        if (lobby.runtime != null) {
            cleanup.execute(lobby.runtime::close);
        }
    }

    private static void error(ConnectionSession participant, String code, String detail) {
        participant.send(new ErrorMessage(code, detail));
    }

    @Override
    public void close() {
        synchronized (this) {
            if (!closed) {
                closed = true;
                for (Lobby lobby : new ArrayList<>(lobbies.values())) {
                    closeLobby(lobby, "Server shutting down");
                }
                cleanup.shutdown();
            }
        }
        boolean interrupted = false;
        while (!cleanup.isTerminated()) {
            try {
                cleanup.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    boolean isTerminated() {
        return cleanup.isTerminated();
    }

    private record Member(ConnectionSession participant, String character, JoinPlayerCommand join) { }

    private static final class Lobby {
        private final String code;
        private final UUID hostConnectionId;
        private final PlayerSlots slots = new PlayerSlots();
        private final Map<UUID, Member> members = new LinkedHashMap<>();
        private boolean running;
        private GameRuntime runtime;

        private Lobby(String code, UUID hostConnectionId) {
            this.code = code;
            this.hostConnectionId = hostConnectionId;
        }
    }
}
