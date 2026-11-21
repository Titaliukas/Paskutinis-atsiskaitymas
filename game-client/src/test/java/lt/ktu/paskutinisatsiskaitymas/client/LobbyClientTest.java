package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class LobbyClientTest {
    @Test
    void waitingSuppressesInputHandlesErrorsAndPreservesSelectionAfterHostClosure() throws Exception {
        GameClientFacade facade = new GameClientFacade();
        BlockingQueue<LobbyList> pages = new LinkedBlockingQueue<>();
        BlockingQueue<LobbyState> states = new LinkedBlockingQueue<>();
        BlockingQueue<ErrorMessage> errors = new LinkedBlockingQueue<>();
        BlockingQueue<LobbyClosed> closures = new LinkedBlockingQueue<>();
        BlockingQueue<ConnectionStatus> statuses = new LinkedBlockingQueue<>();
        try (FacadeWebSocketPeer peer = new FacadeWebSocketPeer()) {
            SwingUtilities.invokeAndWait(() -> {
                facade.selectCharacter("GOGIS");
                facade.changeStat(SelectableStat.HEALTH, 75);
                facade.addObserver(new ClientEvents() {
                    @Override public void onLobbyList(LobbyList page) { pages.add(page); }
                    @Override public void onLobbyState(LobbyState state) {
                        assertTrue(SwingUtilities.isEventDispatchThread());
                        states.add(state);
                    }
                    @Override public void onRequestError(ErrorMessage error) {
                        assertTrue(SwingUtilities.isEventDispatchThread());
                        errors.add(error);
                    }
                    @Override public void onLobbyClosed(LobbyClosed closure) {
                        assertTrue(SwingUtilities.isEventDispatchThread());
                        closures.add(closure);
                    }
                    @Override public void onConnectionStatus(ConnectionStatus status) { statuses.add(status); }
                });
                facade.connect(peer.address());
            });
            assertEquals(new ListLobbies(null), peer.received.poll(5, TimeUnit.SECONDS));
            LobbyList page = new LobbyList(List.of(new LobbySummary("ABC123", "Host", 1)), false);
            peer.send(page);
            assertEquals(page, pages.poll(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> facade.joinLobby("Guest", " abc123 "));
            assertEquals(new JoinLobby("ABC123", "Guest"), peer.received.poll(5, TimeUnit.SECONDS));
            peer.send(new ErrorMessage("LOBBY_FULL", "Room filled"));
            assertEquals("LOBBY_FULL", errors.poll(5, TimeUnit.SECONDS).code());
            assertEquals(new ListLobbies(null), peer.received.poll(5, TimeUnit.SECONDS));
            peer.send(page);
            assertEquals(page, pages.poll(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> facade.joinLobby("Guest", "ABC123"));
            assertEquals(new JoinLobby("ABC123", "Guest"), peer.received.poll(5, TimeUnit.SECONDS));
            UUID host = UUID.randomUUID();
            UUID guest = UUID.randomUUID();
            peer.send(new Welcome(UUID.randomUUID(), guest, 1, "Guest"));
            List<LobbyMember> choosing = List.of(new LobbyMember(host, 0, "Host", "PATASIJUS"),
                    new LobbyMember(guest, 1, "Guest", null));
            peer.send(new LobbyState("ABC123", host, LobbyState.Phase.WAITING, choosing));
            assertEquals(choosing, states.poll(5, TimeUnit.SECONDS).members());
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(facade.canUndoSelection());
                facade.sendInput(new PlayerInput(true, false, true, false, false));
                facade.confirmCharacter();
                facade.confirmCharacter();
            });
            SelectCharacter request = assertInstanceOf(SelectCharacter.class, peer.received.poll(5, TimeUnit.SECONDS));
            assertEquals(new SelectCharacter("GOGIS", new StatAllocation(75, 100, 100)), request);
            assertNull(peer.received.poll(), "While choosing, gameplay and duplicate confirmation must be suppressed");
            peer.send(new ErrorMessage("INVALID_STATS", "Choose again"));
            assertEquals("INVALID_STATS", errors.poll(5, TimeUnit.SECONDS).code());
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(facade.canUndoSelection());
                assertEquals(request.stats(), facade.stats());
                facade.confirmCharacter();
            });
            assertEquals(request, peer.received.poll(5, TimeUnit.SECONDS));
            List<LobbyMember> members = List.of(choosing.getFirst(), new LobbyMember(guest, 1, "Guest", "GOGIS"));
            peer.send(new LobbyState("ABC123", host, LobbyState.Phase.WAITING, members));
            assertEquals(members, states.poll(5, TimeUnit.SECONDS).members());
            SwingUtilities.invokeAndWait(() -> assertFalse(facade.canUndoSelection()));
            peer.send(new LobbyState("ABC123", host, LobbyState.Phase.RUNNING, members));
            assertEquals(LobbyState.Phase.RUNNING, states.poll(5, TimeUnit.SECONDS).phase());
            SwingUtilities.invokeAndWait(() -> facade.sendInput(PlayerInput.NONE));
            assertInstanceOf(InputState.class, peer.received.poll(5, TimeUnit.SECONDS));
            peer.send(new LobbyClosed("The host left. The lobby has closed."));
            assertNotNull(closures.poll(5, TimeUnit.SECONDS));
            assertEquals(new ListLobbies(null), peer.received.poll(5, TimeUnit.SECONDS));
            peer.send(new LobbyList(List.of(), false));
            assertNotNull(pages.poll(5, TimeUnit.SECONDS));
            assertFalse(peer.clientClosed.isDone());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("GOGIS", facade.selectedCharacter());
                assertEquals(request.stats(), facade.stats());
                facade.sendInput(PlayerInput.NONE);
                assertTrue(statuses.stream().anyMatch(status -> status.state() == ConnectionStatus.State.BROWSING
                        && status.detail().contains("host left")));
            });
        } finally {
            SwingUtilities.invokeAndWait(facade::close);
        }
    }

    @Test
    void waitingRoomShowsHostControlAndTreatsNicknameAsPlainText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UUID host = UUID.randomUUID();
            UUID guest = UUID.randomUUID();
            LobbyPanel panel = new LobbyPanel(() -> { });
            LobbyState state = new LobbyState("ABC123", host, LobbyState.Phase.WAITING,
                    List.of(new LobbyMember(host, 0, "<html>Host</html>", "GOGIS"),
                            new LobbyMember(guest, 1, "Guest", "VACIJUS")));
            var actions = (javax.swing.JPanel) panel.getComponent(1);
            var start = (javax.swing.JButton) actions.getComponent(1);
            panel.showLobby(state, guest);
            assertFalse(start.isVisible());
            assertFalse(start.isEnabled());
            panel.showLobby(new LobbyState("ABC123", host, LobbyState.Phase.WAITING,
                    List.of(state.members().getFirst(), new LobbyMember(guest, 1, "Guest", null))), host);
            assertFalse(start.isEnabled());
            panel.showLobby(state, host);
            assertTrue(start.isVisible());
            assertTrue(start.isEnabled());
            var players = (javax.swing.JPanel) panel.getComponent(0);
            var roster = (javax.swing.JPanel) players.getComponent(0);
            assertEquals(4, roster.getComponentCount());
            var hostCard = (javax.swing.JPanel) roster.getComponent(0);
            var portrait = (javax.swing.JLabel) hostCard.getComponent(0);
            var nickname = (javax.swing.JLabel) hostCard.getComponent(1);
            var role = (javax.swing.JLabel) hostCard.getComponent(2);
            assertNotNull(portrait.getIcon());
            assertEquals("GOGIS portrait", portrait.getAccessibleContext().getAccessibleName());
            assertEquals("<html>Host</html>", nickname.getText());
            assertEquals(Boolean.TRUE, nickname.getClientProperty("html.disable"));
            assertNull(nickname.getClientProperty("html"), "Nickname must remain plain text");
            assertEquals("Host \u00B7 You", role.getText());
            var guestCard = (javax.swing.JPanel) roster.getComponent(1);
            assertEquals("VACIJUS portrait", guestCard.getComponent(0).getAccessibleContext().getAccessibleName());
            assertEquals("Guest", ((javax.swing.JLabel) guestCard.getComponent(1)).getText());
            var emptyCard = (javax.swing.JPanel) roster.getComponent(2);
            assertEquals("Waiting for player", ((javax.swing.JLabel) emptyCard.getComponent(1)).getText());
            panel.showLobby(new LobbyState("ABC123", host, LobbyState.Phase.RUNNING, state.members()), host);
            assertFalse(start.isEnabled());
            panel.showLobby(new LobbyState("ABC123", host, LobbyState.Phase.WAITING, List.of(state.members().getFirst())), host);
            var departedCard = (javax.swing.JPanel) roster.getComponent(1);
            assertEquals("Empty player slot", departedCard.getComponent(0).getAccessibleContext().getAccessibleName());
        });
    }

    @Test
    void onlyHostCanCopyExactRoomCode() throws Exception {
        var clipboard = new java.awt.datatransfer.Clipboard("Room test");
        var feedback = new java.util.ArrayList<String>();
        SwingUtilities.invokeAndWait(() -> {
            UUID host = UUID.randomUUID();
            UUID guest = UUID.randomUUID();
            RoomHeader header = new RoomHeader(() -> clipboard, feedback::add);
            LobbyState room = new LobbyState("ABC123", host, LobbyState.Phase.WAITING,
                    List.of(new LobbyMember(host, 0, "Host", null), new LobbyMember(guest, 1, "Guest", null)));
            var copy = (javax.swing.JButton) header.getComponent(1);
            header.showRoom(room, guest);
            assertFalse(copy.isVisible());
            copy.doClick(0);
            assertNull(clipboard.getContents(null));
            header.showRoom(room, host);
            assertTrue(copy.isVisible());
            copy.doClick(0);
            assertEquals(List.of("Room code copied"), feedback);
        });
        assertEquals("ABC123", clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor));
    }

}
