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
        BlockingQueue<LobbyState> states = new LinkedBlockingQueue<>();
        BlockingQueue<ErrorMessage> errors = new LinkedBlockingQueue<>();
        BlockingQueue<LobbyClosed> closures = new LinkedBlockingQueue<>();
        BlockingQueue<ConnectionStatus> statuses = new LinkedBlockingQueue<>();
        try (FacadeWebSocketPeer peer = new FacadeWebSocketPeer()) {
            SwingUtilities.invokeAndWait(() -> {
                facade.selectCharacter("GOGIS");
                facade.changeStat(SelectableStat.HEALTH, 75);
                facade.addObserver(new ClientEvents() {
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
                facade.joinLobby(peer.address(), "Guest", " abc123 ");
            });
            JoinLobby request = assertInstanceOf(JoinLobby.class, peer.received.poll(5, TimeUnit.SECONDS));
            assertEquals("ABC123", request.code());
            assertEquals("GOGIS", request.character());
            assertEquals(new StatAllocation(75, 100, 100), request.stats());
            UUID host = UUID.randomUUID();
            UUID guest = UUID.randomUUID();
            peer.send(new Welcome(UUID.randomUUID(), guest, 1, "Guest"));
            List<LobbyMember> members = List.of(new LobbyMember(host, 0, "Host", "PATASIJUS"),
                    new LobbyMember(guest, 1, "Guest", "GOGIS"));
            peer.send(new LobbyState("ABC123", host, LobbyState.Phase.WAITING, members));
            assertEquals(LobbyState.Phase.WAITING, states.poll(5, TimeUnit.SECONDS).phase());
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(facade.canUndoSelection());
                facade.sendInput(new PlayerInput(true, false, true, false, false));
                facade.startGame();
            });
            assertInstanceOf(StartGame.class, peer.received.poll(5, TimeUnit.SECONDS));
            assertNull(peer.received.poll(), "Waiting input must not be sent");
            peer.send(new ErrorMessage("HOST_REQUIRED", "Only the host can start"));
            assertEquals("HOST_REQUIRED", errors.poll(5, TimeUnit.SECONDS).code());
            peer.send(new LobbyState("ABC123", host, LobbyState.Phase.RUNNING, members));
            assertEquals(LobbyState.Phase.RUNNING, states.poll(5, TimeUnit.SECONDS).phase());
            SwingUtilities.invokeAndWait(() -> facade.sendInput(PlayerInput.NONE));
            assertInstanceOf(InputState.class, peer.received.poll(5, TimeUnit.SECONDS));
            peer.send(new LobbyClosed("The host left. The lobby has closed."));
            assertNotNull(closures.poll(5, TimeUnit.SECONDS));
            peer.clientClosed.get(5, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("GOGIS", facade.selectedCharacter());
                assertEquals(request.stats(), facade.stats());
                facade.sendInput(PlayerInput.NONE);
                assertTrue(statuses.stream().anyMatch(status -> status.state() == ConnectionStatus.State.DISCONNECTED
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
            var actions = (javax.swing.JPanel) panel.getComponent(2);
            var start = (javax.swing.JButton) actions.getComponent(1);
            panel.showLobby(state, guest);
            assertFalse(start.isVisible());
            assertFalse(start.isEnabled());
            panel.showLobby(state, host);
            assertTrue(start.isVisible());
            assertTrue(start.isEnabled());
            var players = (javax.swing.JPanel) panel.getComponent(1);
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
            assertEquals("Host · You", role.getText());
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
}
