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
    private Peer host;
    private boolean running;

    @Test
    void customBuildIsAppliedOverTheActualWebSocketJoinAndInputPath() throws Exception {
        Peer peer = connect("/game");
        admit(peer, new CreateLobby("Custom", "GOGIS", new StatAllocation(125, 125, 50)));
        Welcome welcome = peer.receive(Welcome.class, value -> true);
        startGame();
        WorldSnapshot joined = peer.receive(WorldSnapshot.class, value -> !value.players().isEmpty());
        assertEquals(125, player(joined, welcome).health());
        assertEquals(125, player(joined, welcome).maxHealth());
        peer.send(new InputState(0, false, true, false, false, false));
        WorldSnapshot moved = peer.receive(WorldSnapshot.class,
                value -> !value.players().isEmpty() && player(value, welcome).velocityX() > 0);
        assertEquals(260 * 1.25, player(moved, welcome).velocityX(), 1e-9);
    }

    @Test
    void examinatorInputSnapshotAndProneDamageAreAuthoritativeAndLobbyScoped() throws Exception {
        server.close();
        server = new GameWebSocketServer(new InetSocketAddress("127.0.0.1", 0), ArenaSelection.create("training", null));
        server.start(); server.awaitStarted(Duration.ofSeconds(5));
        Peer owner = connect("/game");
        admit(owner, new CreateLobby("Inspector", "VACIJUS"));
        Welcome inspector = owner.receive(Welcome.class, value -> true);
        Peer standing = connect("/game");
        admit(standing, new CreateLobby("Standing"));
        Welcome standingId = standing.receive(Welcome.class, value -> true);
        Peer dodge = connect("/game");
        admit(dodge, new CreateLobby("Dodge"));
        Welcome dodgeId = dodge.receive(Welcome.class, value -> true);
        Peer isolated = connect("/game");
        isolated.send(new CreateLobby("Isolated"));
        Welcome isolatedId = isolated.receive(Welcome.class, value -> true);
        isolated.send(new StartGame());
        startGame();
        owner.receive(WorldSnapshot.class, value -> value.players().size() == 3);
        dodge.send(new InputState(0, false, false, false, false, false, false, true));
        owner.receive(WorldSnapshot.class, value -> player(value, dodgeId).prone());
        owner.send(new InputState(0, false, false, false, false, false, true, false));
        WorldSnapshot scan = owner.receive(WorldSnapshot.class, value -> value.examinators().stream()
                .anyMatch(ability -> ability.ownerId().equals(inspector.playerId()) && ability.phase().equals("INSPECT")));
        assertEquals(100, player(scan, standingId).health());
        WorldSnapshot fired = owner.receive(WorldSnapshot.class, value -> value.examinators().stream()
                .anyMatch(ability -> ability.ownerId().equals(inspector.playerId()) && ability.phase().equals("FIRE")));
        var volley = fired.examinators().stream().filter(e -> e.ownerId().equals(inspector.playerId())).findFirst().orElseThrow();
        assertEquals(1, volley.shotSequence());
        assertEquals(1, volley.shots().size());
        assertEquals(70, player(fired, standingId).health());
        assertEquals(100, player(fired, dodgeId).health());
        assertTrue(player(fired, dodgeId).prone());
        WorldSnapshot shared = dodge.receive(WorldSnapshot.class, value -> value.examinators().stream()
                .anyMatch(e -> e.ownerId().equals(inspector.playerId()) && e.phase().equals("FIRE")));
        assertEquals(volley.shots().getFirst().endpointX(), shared.examinators().stream()
                .filter(e -> e.ownerId().equals(inspector.playerId())).findFirst().orElseThrow().shots().getFirst().endpointX());
        assertEquals(487, volley.shots().getFirst().originX());
        assertEquals(267, volley.shots().getFirst().originY());
        WorldSnapshot other = isolated.receive(WorldSnapshot.class, value -> value.tick() > fired.tick());
        assertEquals(List.of(isolatedId.playerId()), other.examinators().stream().map(ExaminatorSnapshot::ownerId).toList());
        assertEquals(100, other.players().getFirst().health());
        assertTrue(other.examinators().stream().allMatch(e -> e.shotSequence() == 0));
        WorldSnapshot expired = owner.receive(WorldSnapshot.class, value -> value.tick() > fired.tick() + 18);
        assertTrue(expired.examinators().stream().allMatch(e -> e.shots().isEmpty()));
        assertEquals(70, player(expired, standingId).health(), "Held activation cannot restart or repeat fire");
    }

    @BeforeEach
    void start() throws Exception {
        server = new GameWebSocketServer(new InetSocketAddress("127.0.0.1", 0), ArenaSelection.create("combat", null));
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
    void trainingSelectionReachesTheRealLoopAndWireWithoutNPCs() throws Exception {
        server.close();
        assertTrue(server.isGameLoopTerminated());
        server = new GameWebSocketServer(new InetSocketAddress("127.0.0.1", 0),
                ArenaSelection.create("training", null));
        server.start();
        server.awaitStarted(Duration.ofSeconds(5));
        Peer peer = connect("/game");
        admit(peer, new CreateLobby("Training"));
        peer.receive(Welcome.class, value -> true);
        startGame();
        WorldSnapshot snapshot = peer.receive(WorldSnapshot.class, value -> value.players().size() == 1);
        assertEquals("training", snapshot.arena().arenaType());
        assertEquals(new PlatformSnapshot(0, 448, 960, 92), snapshot.arena().platforms().getFirst());
        assertEquals(3, snapshot.arena().platforms().size());
        WorldSnapshot later = peer.receive(WorldSnapshot.class, value -> value.tick() > snapshot.tick()
                && value.arena().platforms().get(1).x() != snapshot.arena().platforms().get(1).x()
                && value.arena().platforms().get(2).y() != snapshot.arena().platforms().get(2).y());
        assertEquals(snapshot.arena().platforms().getFirst(), later.arena().platforms().getFirst());
        assertTrue(snapshot.npcs().isEmpty());
        assertEquals(384, snapshot.players().getFirst().y());
    }

    @Test
    void twoPlayersReceiveSnapshotsAndMoveIndependently() throws Exception {
        Peer first = connect("/game");
        Peer second = connect("/game");
        admit(first, new CreateLobby("First"));
        admit(second, new CreateLobby("Second"));
        Welcome a = first.receive(Welcome.class, value -> value.nickname().equals("First"));
        Welcome b = second.receive(Welcome.class, value -> value.nickname().equals("Second"));
        assertNotEquals(a.playerId(), b.playerId());
        assertNotEquals(a.slot(), b.slot());

        startGame();
        WorldSnapshot initial = first.receive(WorldSnapshot.class, value -> value.players().size() == 2);
        assertEquals(List.of(
                new PlatformSnapshot(45, 215, 870, 6),
                new PlatformSnapshot(333, 344, 294, 6),
                new PlatformSnapshot(210, 280, 120, 6, "service"),
                new PlatformSnapshot(0, 421, 960, 119)), initial.arena().platforms().subList(0, 4));
        assertEquals(6, initial.arena().platforms().size());
        assertEquals("combat", initial.arena().arenaType());
        assertEquals(2, initial.npcs().size());
        assertTrue(initial.npcs().stream().allMatch(npc -> List.of("PATROL", "CHASE", "ATTACK", "FLEE")
                .contains(npc.activity())));
        WorldSnapshot secondView = second.receive(WorldSnapshot.class, value -> value.npcs().size() == 2);
        assertEquals(initial.npcs().stream().map(NPCSnapshot::id).toList(),
                secondView.npcs().stream().map(NPCSnapshot::id).toList());
        double firstX = player(initial, a).x();
        double secondX = player(initial, b).x();
        first.send(new InputState(0, false, true, false, false, false));
        WorldSnapshot moved = first.receive(WorldSnapshot.class,
                value -> value.players().size() == 2 && player(value, a).x() > firstX);
        assertEquals(secondX, player(moved, b).x(), 0.000_001);

        first.send(new InputState(1, false, false, false, false, false));
        first.send(new Ping("one"));
        assertEquals(new Pong("one"), first.receive(Pong.class, value -> value.requestId().equals("one")));
    }

    @Test
    void playersCanChooseTheSameCharacterAndSnapshotsKeepTheirChoice() throws Exception {
        Peer first = connect("/game");
        Peer second = connect("/game");
        admit(first, new CreateLobby("First", "VACIJUS"));
        admit(second, new CreateLobby("Second", "VACIJUS"));
        first.receive(Welcome.class, value -> value.nickname().equals("First"));
        second.receive(Welcome.class, value -> value.nickname().equals("Second"));

        startGame();
        WorldSnapshot snapshot = first.receive(WorldSnapshot.class,
                value -> value.players().size() == 2
                        && value.players().stream().allMatch(player -> "VACIJUS".equals(player.character())));

        assertEquals(2, snapshot.players().size());
    }

    @Test
    void twoClientsReceiveIdenticalProjectileAndSlowdownFactsForTheSameTicks() throws Exception {
        Peer first = connect("/game");
        Peer second = connect("/game");
        admit(first, new CreateLobby("First"));
        admit(second, new CreateLobby("Second"));
        first.receive(Welcome.class, value -> true);
        second.receive(Welcome.class, value -> true);
        startGame();
        WorldSnapshot flying = first.receive(WorldSnapshot.class,
                value -> value.players().size() == 2 && !value.projectiles().isEmpty());
        WorldSnapshot otherFlying = second.receive(WorldSnapshot.class, value -> value.tick() == flying.tick());
        assertEquals(flying, otherFlying);
        assertTrue(flying.projectiles().stream().allMatch(shot -> shot.projectileType().equals("BOOK")));
        WorldSnapshot slowed = first.receive(WorldSnapshot.class,
                value -> value.players().size() == 2 && value.players().stream().anyMatch(PlayerSnapshot::slowed));
        WorldSnapshot otherSlowed = second.receive(WorldSnapshot.class, value -> value.tick() == slowed.tick());
        assertEquals(slowed, otherSlowed);
        assertTrue(slowed.projectiles().size() <= 8);
    }

    @Test
    void fourPlayersReceiveUniqueSlotsAndMoveIndependently() throws Exception {
        List<Peer> players = new ArrayList<>();
        List<Welcome> welcomes = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Peer peer = connect("/game");
            admit(peer, new CreateLobby("Player " + index));
            players.add(peer);
            welcomes.add(peer.receive(Welcome.class, value -> true));
        }
        assertEquals(4, welcomes.stream().map(Welcome::playerId).distinct().count());
        assertEquals(List.of(0, 1, 2, 3), welcomes.stream().map(Welcome::slot).sorted().toList());
        startGame();
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
            players.get(index).send(new InputState(0, false, true, false, false, false));
            WorldSnapshot moved = observer.receive(WorldSnapshot.class,
                    value -> value.tick() > before.tick() && value.players().size() == 4
                            && player(value, active).x() > player(before, active).x());
            for (Welcome other : welcomes) {
                if (!other.playerId().equals(active.playerId())) {
                    assertEquals(player(before, other).x(), player(moved, other).x(), 0.000_001);
                }
            }
            players.get(index).send(new InputState(1, false, false, false, false, false));
            observer.receive(WorldSnapshot.class,
                    value -> value.tick() > moved.tick() && player(value, active).velocityX() == 0);
        }
    }

    @Test
    void waitingLobbyReusesGuestSlotButRunningLobbyRejectsAllNewEntries() throws Exception {
        Peer first = connect("/game");
        admit(first, new CreateLobby("Host"));
        first.receive(Welcome.class, value -> true);
        List<Peer> guests = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Peer guest = connect("/game");
            admit(guest, new CreateLobby("Guest " + index));
            guest.receive(Welcome.class, value -> true);
            guests.add(guest);
        }
        Peer fifth = connect("/game");
        fifth.send(new JoinLobby(first.code.get(), "Fifth"));
        assertEquals("LOBBY_FULL", fifth.receive(ErrorMessage.class, value -> true).code());
        guests.getFirst().socket.sendClose(1000, "leave").get(5, TimeUnit.SECONDS);
        first.receive(LobbyState.class, value -> value.members().size() == 3);
        fifth.send(new JoinLobby(first.code.get(), "Replacement"));
        Welcome replacement = fifth.receive(Welcome.class, value -> true);
        assertEquals(1, replacement.slot());
        startGame();
        first.receive(WorldSnapshot.class, value -> value.players().size() == 4);
        fifth.socket.sendClose(1000, "leave").get(5, TimeUnit.SECONDS);
        first.receive(WorldSnapshot.class, value -> value.players().size() == 3);
        Peer late = connect("/game");
        late.send(new JoinLobby(first.code.get(), "Replacement"));
        assertEquals("LOBBY_STARTED", late.receive(ErrorMessage.class, value -> true).code());
    }

    @Test
    void validatesDirectionAndMalformedMessagesWithoutLosingConnection() throws Exception {
        Peer peer = connect("/game");
        peer.send(new InputState(0, false, true, false, false, false));
        assertEquals("HANDSHAKE_REQUIRED", peer.receive(ErrorMessage.class,
                value -> value.code().equals("HANDSHAKE_REQUIRED")).code());
        peer.socket.sendText("not-json", true).get(5, TimeUnit.SECONDS);
        assertEquals("INVALID_MESSAGE", peer.receive(ErrorMessage.class,
                value -> value.code().equals("INVALID_MESSAGE")).code());
        admit(peer, new CreateLobby("Player"));
        peer.receive(Welcome.class, value -> true);
        peer.send(new CreateLobby("Replacement"));
        assertEquals("ALREADY_CONNECTED", peer.receive(ErrorMessage.class,
                value -> value.code().equals("ALREADY_CONNECTED")).code());
        peer.send(new WorldSnapshot(0, new ArenaSnapshot("combat", 1, 1, List.of()), List.of(), List.of(), List.of(), List.of(), List.of()));
        assertEquals("UNEXPECTED_MESSAGE", peer.receive(ErrorMessage.class,
                value -> value.code().equals("UNEXPECTED_MESSAGE")).code());
    }

    @Test
    void reassemblesFragmentedJoinAndRejectsWrongPathAndBinary() throws Exception {
        Peer fragmented = connect("/game");
        fragmented.socket.sendText("{\"type\":\"CREATE_LOBBY\",", false).get(5, TimeUnit.SECONDS);
        fragmented.socket.sendText("\"nickname\":\"Fragmented\",\"character\":\"PATASIJUS\"}", true)
                .get(5, TimeUnit.SECONDS);
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
        startGame();
        peer.receive(WorldSnapshot.class, value -> true);
        server.close();
        assertEquals(1001, peer.closed.get(5, TimeUnit.SECONDS));
        assertTrue(server.isGameLoopTerminated());
        server = null;
        assertTrue(Thread.getAllStackTraces().keySet().stream()
                .noneMatch(thread -> thread.isAlive() && thread.getName().equals("authoritative-game-loop")));
    }

    @Test
    void independentLobbiesIsolateWorldsEventsAndHostClosure() throws Exception {
        Peer firstHost = connect("/game");
        firstHost.send(new CreateLobby("Alpha"));
        Welcome alpha = firstHost.receive(Welcome.class, value -> true);
        String firstCode = firstHost.code.get(5, TimeUnit.SECONDS);
        Peer guest = connect("/game");
        guest.send(new JoinLobby(firstCode, "AlphaGuest"));
        Welcome alphaGuest = guest.receive(Welcome.class, value -> true);
        firstHost.receive(LobbyState.class, value -> value.members().size() == 2);
        Peer secondHost = connect("/game");
        secondHost.send(new CreateLobby("Beta"));
        Welcome beta = secondHost.receive(Welcome.class, value -> true);
        String secondCode = secondHost.code.get(5, TimeUnit.SECONDS);
        assertNotEquals(firstCode, secondCode);
        firstHost.send(new StartGame());
        secondHost.send(new StartGame());
        WorldSnapshot first = firstHost.receive(WorldSnapshot.class, value -> value.players().size() == 2);
        WorldSnapshot second = secondHost.receive(WorldSnapshot.class, value -> value.players().size() == 1);
        assertEquals(List.of(alpha.playerId(), alphaGuest.playerId()),
                first.players().stream().map(PlayerSnapshot::playerId).toList());
        assertEquals(beta.playerId(), second.players().getFirst().playerId());
        firstHost.send(new InputState(0, false, true, false, false, false));
        firstHost.receive(WorldSnapshot.class, value -> player(value, alpha).x() > player(first, alpha).x());
        WorldSnapshot unchanged = secondHost.receive(WorldSnapshot.class, value -> value.tick() > second.tick() + 3);
        assertEquals(player(second, beta).x(), player(unchanged, beta).x());
        firstHost.socket.sendClose(1000, "host leaves").get(5, TimeUnit.SECONDS);
        assertTrue(guest.receive(LobbyClosed.class, value -> true).reason().contains("host"));
        secondHost.send(new Ping("still-alive"));
        secondHost.receive(Pong.class, value -> value.requestId().equals("still-alive"));
        secondHost.receive(WorldSnapshot.class, value -> value.tick() > unchanged.tick());
        assertTrue(firstHost.observed.stream().filter(GameEventMessage.class::isInstance)
                .map(GameEventMessage.class::cast).anyMatch(event -> event.text().contains("Alpha")));
        assertTrue(secondHost.observed.stream().filter(GameEventMessage.class::isInstance)
                .map(GameEventMessage.class::cast).anyMatch(event -> event.text().contains("Beta")));
        assertFalse(secondHost.observed.stream().filter(GameEventMessage.class::isInstance)
                .map(GameEventMessage.class::cast).anyMatch(event -> event.text().contains("Alpha")));
        assertTrue(secondHost.observed.stream().filter(WorldSnapshot.class::isInstance).map(WorldSnapshot.class::cast)
                .allMatch(world -> world.players().stream().allMatch(player -> player.playerId().equals(beta.playerId()))));
        assertFalse(secondHost.observed.stream().anyMatch(LobbyClosed.class::isInstance));
        server.close();
        assertTrue(server.isGameLoopTerminated());
        server = null;
    }

    @Test
    void waitingHostDepartureClosesOnlyItsLobbyAndNoWorldRunsBeforeStart() throws Exception {
        Peer first = connect("/game");
        first.send(new CreateLobby("Host"));
        first.receive(Welcome.class, value -> true);
        Peer guest = connect("/game");
        guest.send(new JoinLobby(first.code.get(5, TimeUnit.SECONDS), "Guest"));
        guest.receive(Welcome.class, value -> true);
        guest.send(new StartGame());
        assertEquals("HOST_REQUIRED", guest.receive(ErrorMessage.class, value -> true).code());
        guest.send(new InputState(0, true, false, false, false, false));
        assertEquals("GAME_NOT_STARTED", guest.receive(ErrorMessage.class, value -> true).code());
        guest.send(new Ping("waiting"));
        guest.receive(Pong.class, value -> true);
        assertFalse(guest.observed.stream().anyMatch(WorldSnapshot.class::isInstance));
        first.socket.sendClose(1000, "leave").get(5, TimeUnit.SECONDS);
        guest.receive(LobbyClosed.class, value -> true);
        guest.send(new JoinLobby(first.code.get(), "Again"));
        assertEquals("LOBBY_NOT_FOUND", guest.receive(ErrorMessage.class, value -> true).code());
        guest.send(new CreateLobby("New host"));
        guest.receive(Welcome.class, value -> value.nickname().equals("New host"));
    }

    private void admit(Peer peer, CreateLobby selection) throws Exception {
        if (host == null) {
            host = peer;
            peer.send(selection);
        } else {
            peer.send(new JoinLobby(host.code.get(5, TimeUnit.SECONDS), selection.nickname(),
                    selection.character(), selection.stats()));
        }
        peer.code.get(5, TimeUnit.SECONDS);
    }

    private void startGame() throws Exception {
        if (!running) {
            running = true;
            host.send(new StartGame());
        }
    }

    private Peer joined(String nickname) throws Exception {
        Peer peer = connect("/game");
        admit(peer, new CreateLobby(nickname));
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
        private final List<Message> observed = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final StringBuilder fragments = new StringBuilder();
        private WebSocket socket;
        private final CompletableFuture<String> code = new CompletableFuture<>();

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
                String json = fragments.toString();
                try {
                    Message decoded = codec.decode(json);
                    observed.add(decoded);
                    if (decoded instanceof LobbyState state) {
                        code.complete(state.code());
                    }
                } catch (ProtocolException exception) {
                    code.completeExceptionally(exception);
                }
                inbox.add(json);
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
