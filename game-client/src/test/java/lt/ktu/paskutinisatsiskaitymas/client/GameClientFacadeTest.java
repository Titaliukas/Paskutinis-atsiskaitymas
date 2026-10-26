package lt.ktu.paskutinisatsiskaitymas.client;

import java.net.URI;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.ArenaSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import lt.ktu.paskutinisatsiskaitymas.protocol.InputState;
import lt.ktu.paskutinisatsiskaitymas.protocol.StatAllocation;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GameClientFacadeTest {
    @Test
    void selectionCommandsUndoExactStateAndIgnoreNoOps() throws Exception {
        GameClientFacade facade = new GameClientFacade();
        try {
            edt(() -> {
                assertEquals(Hello.DEFAULT_CHARACTER, facade.selectedCharacter());
                assertEquals(StatAllocation.DEFAULT, facade.stats());
                facade.undoSelection();
                facade.selectCharacter(Hello.DEFAULT_CHARACTER);
                facade.changeStat(SelectableStat.HEALTH, 100);
                assertFalse(facade.canUndoSelection());
                facade.changeStat(SelectableStat.HEALTH, 75);
                facade.changeStat(SelectableStat.SPEED, 125);
                facade.selectCharacter("GOGIS");
                assertEquals(StatAllocation.DEFAULT, facade.stats());
                facade.selectCharacter("GOGIS");
                facade.undoSelection();
                assertEquals(Hello.DEFAULT_CHARACTER, facade.selectedCharacter());
                assertEquals(new StatAllocation(75, 125, 100), facade.stats());
                facade.changeStat(SelectableStat.SPEED, 125);
                facade.undoSelection();
                assertEquals(new StatAllocation(75, 100, 100), facade.stats());
                facade.undoSelection();
                assertEquals(StatAllocation.DEFAULT, facade.stats());
                assertFalse(facade.canUndoSelection());
            });
        } finally {
            edt(facade::close);
        }
    }

    @Test
    void invalidChangesPreserveStateAndHistoryAndEnforceExistingLimits() throws Exception {
        GameClientFacade facade = new GameClientFacade();
        try {
            edt(() -> {
                facade.selectCharacter("VACIJUS");
                assertThrows(IllegalArgumentException.class, () -> facade.selectCharacter("UNKNOWN"));
                assertThrows(IllegalArgumentException.class, () -> facade.selectCharacter(null));
                for (int value : new int[]{45, 155, 51, 105}) {
                    assertThrows(IllegalArgumentException.class, () -> facade.changeStat(SelectableStat.HEALTH, value));
                }
                assertThrows(NullPointerException.class, () -> facade.changeStat(null, 100));
                assertEquals("VACIJUS", facade.selectedCharacter());
                assertEquals(StatAllocation.DEFAULT, facade.stats());
                facade.undoSelection();
                assertFalse(facade.canUndoSelection());
                facade.changeStat(SelectableStat.HEALTH, 50);
                facade.changeStat(SelectableStat.JUMP_HEIGHT, 150);
                assertEquals(new StatAllocation(50, 100, 150), facade.stats());
            });
            assertThrows(IllegalStateException.class, facade::selectedCharacter);
        } finally {
            edt(facade::close);
        }
    }

    @Test
    void facadeInstancesHaveIndependentSelectionHistoryAndConnections() throws Exception {
        GameClientFacade first = new GameClientFacade();
        GameClientFacade second = new GameClientFacade();
        try (FacadeWebSocketPeer peer = new FacadeWebSocketPeer()) {
            Events events = new Events(second);
            edt(() -> {
                first.selectCharacter("GOGIS");
                first.changeStat(SelectableStat.HEALTH, 75);
                second.selectCharacter("VACIJUS");
                second.addObserver(events);
                second.connect(peer.address(), "Second");
                first.close();
            });
            assertEquals("VACIJUS", nextHello(peer).character());
            peer.send(welcome("Second"));
            events.joined.get(5, TimeUnit.SECONDS);
            edt(() -> {
                assertEquals("GOGIS", first.selectedCharacter());
                assertTrue(first.canUndoSelection(), "Closing one facade must not clear its selection history");
                assertFalse(second.canUndoSelection());
                second.changeStat(SelectableStat.HEALTH, 75);
                second.undoSelection();
                assertEquals(StatAllocation.DEFAULT, second.stats());
                assertEquals(new StatAllocation(75, 100, 100), first.stats());
            });
        } finally {
            edt(() -> {
                first.close();
                second.close();
            });
        }
    }

    @Test
    void welcomeClearsHistoryBeforeObserversAndForwardsSelectionInputAndEventsExactlyOnce() throws Exception {
        GameClientFacade facade = new GameClientFacade();
        try (FacadeWebSocketPeer peer = new FacadeWebSocketPeer()) {
            Events events = new Events(facade);
            CompletableFuture<Welcome> secondObserver = new CompletableFuture<>();
            edt(() -> {
                facade.selectCharacter("JURGELINIS");
                facade.changeStat(SelectableStat.HEALTH, 75);
                facade.changeStat(SelectableStat.SPEED, 125);
                facade.addObserver(events);
                facade.addObserver(new ClientEvents() {
                    @Override
                    public void onJoined(Welcome welcome) {
                        assertFalse(facade.canUndoSelection());
                        secondObserver.complete(welcome);
                    }
                });
                facade.connect(peer.address(), "Chosen");
                assertTrue(facade.canUndoSelection(), "Starting transport must retain history");
                facade.sendInput(PlayerInput.NONE);
            });
            Hello hello = nextHello(peer);
            assertEquals(new Hello("Chosen", "JURGELINIS", new StatAllocation(75, 125, 100)), hello);
            assertNull(peer.received.poll(), "Input before WELCOME must be ignored");
            Welcome welcome = welcome("Chosen");
            WorldSnapshot world = snapshot();
            GameEventMessage notification = new GameEventMessage("TEST", "Facade notification");
            peer.send(welcome);
            peer.send(world);
            peer.send(notification);
            assertEquals(welcome, events.joined.get(5, TimeUnit.SECONDS));
            assertEquals(welcome, secondObserver.get(5, TimeUnit.SECONDS));
            assertEquals(world, events.snapshot.get(5, TimeUnit.SECONDS));
            assertEquals(notification, events.notification.get(5, TimeUnit.SECONDS));
            edt(() -> {
                assertEquals(List.of("joined", "CONNECTED", "snapshot", "event"), events.gameplay);
                assertTrue(events.allOnEdt);
                facade.sendInput(new PlayerInput(true, false, true, false, true));
            });
            assertEquals(new InputState(0, true, false, true, false, true), peer.received.poll(5, TimeUnit.SECONDS));
            edt(() -> {
                facade.disconnect();
                assertEquals("JURGELINIS", facade.selectedCharacter());
                assertEquals(hello.stats(), facade.stats());
                assertFalse(facade.canUndoSelection());
                facade.changeStat(SelectableStat.HEALTH, 70);
                facade.undoSelection();
                assertEquals(hello.stats(), facade.stats());
                assertFalse(facade.canUndoSelection());
            });
            peer.clientClosed.get(5, TimeUnit.SECONDS);
            edt(() -> {
                int count = events.statuses.size();
                facade.close();
                facade.close();
                assertEquals(count, events.statuses.size(), "Close after disconnect must not duplicate status");
            });
        } finally {
            edt(facade::close);
        }
    }

    @Test
    void invalidRejectedAndCancelledConnectionsKeepHistory() throws Exception {
        GameClientFacade facade = new GameClientFacade();
        try (FacadeWebSocketPeer rejected = new FacadeWebSocketPeer();
                FacadeWebSocketPeer cancelled = new FacadeWebSocketPeer()) {
            Events events = new Events(facade);
            edt(() -> {
                facade.selectCharacter("VACIJUS");
                facade.addObserver(events);
                assertThrows(IllegalArgumentException.class,
                        () -> facade.connect(URI.create("http://localhost/game"), "Invalid"));
                assertThrows(IllegalArgumentException.class, () -> facade.connect(rejected.address(), " "));
                assertTrue(facade.canUndoSelection());
                facade.connect(rejected.address(), "Rejected");
            });
            nextHello(rejected);
            rejected.send(new ErrorMessage("SERVER_FULL", "No free slot"));
            events.error.get(5, TimeUnit.SECONDS);
            edt(() -> {
                assertTrue(facade.canUndoSelection());
                facade.connect(cancelled.address(), "Cancelled");
            });
            nextHello(cancelled);
            edt(() -> {
                facade.disconnect();
                assertTrue(facade.canUndoSelection());
                facade.undoSelection();
                assertEquals(Hello.DEFAULT_CHARACTER, facade.selectedCharacter());
                assertFalse(facade.canUndoSelection());
            });
            cancelled.clientClosed.get(5, TimeUnit.SECONDS);
            assertFalse(events.joined.isDone());
        } finally {
            edt(facade::close);
        }
    }

    @Test
    void queuedOldWelcomeSnapshotStatusAndNotificationCannotAffectNewAttempt() throws Exception {
        GameClientFacade facade = new GameClientFacade();
        try (FacadeWebSocketPeer old = new FacadeWebSocketPeer();
                FacadeWebSocketPeer current = new FacadeWebSocketPeer()) {
            Events events = new Events(facade);
            edt(() -> {
                facade.selectCharacter("GOGIS");
                facade.addObserver(events);
                facade.connect(old.address(), "Old");
            });
            nextHello(old);
            edt(() -> {
                try {
                    old.send(welcome("Old"));
                    old.send(snapshot());
                    old.send(new GameEventMessage("OLD", "Must be discarded"));
                    old.ping();
                    // Test-only EDT gate: PONG proves callbacks return without waiting for the EDT.
                    old.pong.get(5, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
                facade.disconnect();
                facade.selectCharacter("VACIJUS");
                facade.connect(current.address(), "Current");
            });
            assertEquals("VACIJUS", nextHello(current).character());
            edt(() -> {
                assertTrue(facade.canUndoSelection(), "Queued old WELCOME must not clear new history");
                assertTrue(events.gameplay.isEmpty(), "All old queued gameplay/status events must be dropped");
            });
            Welcome welcome = welcome("Current");
            current.send(welcome);
            assertEquals(welcome, events.joined.get(5, TimeUnit.SECONDS));
            edt(() -> assertFalse(facade.canUndoSelection()));
        } finally {
            edt(facade::close);
        }
    }

    @Test
    void transportFailurePreservesUndoHistory() throws Exception {
        URI unavailable;
        try (ServerSocket reservation = new ServerSocket(0)) {
            unavailable = URI.create("ws://localhost:" + reservation.getLocalPort() + "/game");
        }
        GameClientFacade facade = new GameClientFacade();
        Events events = new Events(facade);
        try {
            edt(() -> {
                facade.selectCharacter("GOGIS");
                facade.addObserver(events);
                facade.connect(unavailable, "Unavailable");
            });
            events.error.get(5, TimeUnit.SECONDS);
            edt(() -> {
                assertTrue(facade.canUndoSelection());
                facade.undoSelection();
                assertEquals(Hello.DEFAULT_CHARACTER, facade.selectedCharacter());
            });
        } finally {
            edt(facade::close);
        }
    }

    @Test
    void closeReleasesActiveTransportIsIdempotentAndPreventsReconnect() throws Exception {
        GameClientFacade facade = new GameClientFacade();
        try (FacadeWebSocketPeer peer = new FacadeWebSocketPeer()) {
            Events events = new Events(facade);
            edt(() -> {
                facade.addObserver(events);
                facade.connect(peer.address(), "Closing");
            });
            nextHello(peer);
            peer.send(welcome("Closing"));
            events.joined.get(5, TimeUnit.SECONDS);
            edt(() -> {
                facade.close();
                facade.close();
                assertThrows(IllegalStateException.class, () -> facade.connect(peer.address(), "Again"));
            });
            peer.clientClosed.get(5, TimeUnit.SECONDS);
        } finally {
            edt(facade::close);
        }
    }

    private static void edt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static Hello nextHello(FacadeWebSocketPeer peer) throws Exception {
        return assertInstanceOf(Hello.class, peer.received.poll(5, TimeUnit.SECONDS));
    }

    private static Welcome welcome(String nickname) {
        return new Welcome(UUID.randomUUID(), UUID.randomUUID(), 0, nickname);
    }

    private static WorldSnapshot snapshot() {
        return new WorldSnapshot(7, new ArenaSnapshot("training", 960, 540, List.of()),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static final class Events implements ClientEvents {
        private final GameClientFacade facade;
        private final CompletableFuture<Welcome> joined = new CompletableFuture<>();
        private final CompletableFuture<WorldSnapshot> snapshot = new CompletableFuture<>();
        private final CompletableFuture<GameEventMessage> notification = new CompletableFuture<>();
        private final CompletableFuture<ConnectionStatus> error = new CompletableFuture<>();
        private final List<String> gameplay = new ArrayList<>();
        private final List<ConnectionStatus> statuses = new ArrayList<>();
        private boolean allOnEdt = true;

        private Events(GameClientFacade facade) {
            this.facade = facade;
        }

        @Override
        public void onConnectionStatus(ConnectionStatus status) {
            allOnEdt &= SwingUtilities.isEventDispatchThread();
            statuses.add(status);
            if (status.state() == ConnectionStatus.State.CONNECTED) {
                assertFalse(facade.canUndoSelection());
                gameplay.add("CONNECTED");
            } else if (status.state() == ConnectionStatus.State.ERROR) {
                error.complete(status);
            }
        }

        @Override
        public void onJoined(Welcome welcome) {
            allOnEdt &= SwingUtilities.isEventDispatchThread();
            assertFalse(facade.canUndoSelection(), "Clear history before notifying any facade client");
            gameplay.add("joined");
            joined.complete(welcome);
        }

        @Override
        public void onSnapshot(WorldSnapshot world) {
            allOnEdt &= SwingUtilities.isEventDispatchThread();
            gameplay.add("snapshot");
            snapshot.complete(world);
        }

        @Override
        public void onGameEvent(GameEventMessage event) {
            allOnEdt &= SwingUtilities.isEventDispatchThread();
            gameplay.add("event");
            notification.complete(event);
        }
    }
}
