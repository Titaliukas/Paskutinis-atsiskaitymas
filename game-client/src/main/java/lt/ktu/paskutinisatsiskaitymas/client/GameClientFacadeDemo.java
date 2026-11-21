package lt.ktu.paskutinisatsiskaitymas.client;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyState;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyList;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** Second facade client: uses the EDT for local state, with bounded network waits on the main thread. */
public final class GameClientFacadeDemo {
    private GameClientFacadeDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: GameClientFacadeDemo [server-address] [nickname]");
        }
        URI address = URI.create(args.length > 0 ? args[0] : "ws://localhost:8080/game");
        String nickname = args.length > 1 ? args[1] : "FacadeDemo";
        GameClientFacade facade = new GameClientFacade();
        CompletableFuture<Welcome> joined = new CompletableFuture<>();
        CompletableFuture<WorldSnapshot> snapshot = new CompletableFuture<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                facade.addObserver(new ClientEvents() {
                    private boolean connecting;

                    @Override
                    public void onConnectionStatus(ConnectionStatus status) {
                        System.out.println(status.state() + ": " + status.detail());
                        if (status.state() == ConnectionStatus.State.ERROR
                                || connecting && status.state() == ConnectionStatus.State.DISCONNECTED) {
                            IllegalStateException error = new IllegalStateException(status.detail());
                            joined.completeExceptionally(error);
                            snapshot.completeExceptionally(error);
                        }
                        connecting = status.state() == ConnectionStatus.State.CONNECTING
                                || status.state() == ConnectionStatus.State.CONNECTED
                                || status.state() == ConnectionStatus.State.LOBBY
                                || status.state() == ConnectionStatus.State.SELECTING
                                || status.state() == ConnectionStatus.State.BROWSING;
                    }

                    @Override
                    public void onJoined(Welcome welcome) {
                        joined.complete(welcome);
                    }

                    @Override
                    public void onLobbyList(LobbyList page) {
                        facade.createLobby(nickname);
                    }

                    @Override
                    public void onRequestError(ErrorMessage error) {
                        snapshot.completeExceptionally(new IllegalStateException(error.message()));
                    }

                    @Override
                    public void onLobbyState(LobbyState state) {
                        SwingUtilities.invokeLater(() -> handleLobbyState(state));
                    }

                    private void handleLobbyState(LobbyState state) {
                        if (state.phase() != LobbyState.Phase.WAITING) {
                            return;
                        }
                        if (state.members().getFirst().character() == null) {
                            System.out.println("LOBBY: " + state.code());
                            facade.selectCharacter("VACIJUS");
                            facade.changeStat(SelectableStat.HEALTH, 75);
                            facade.changeStat(SelectableStat.SPEED, 125);
                            printSelection("Customized", facade);
                            facade.selectCharacter("GOGIS");
                            printSelection("Changed character", facade);
                            facade.undoSelection();
                            printSelection("Undo restored", facade);
                            facade.confirmCharacter();
                        } else if (state.members().stream().allMatch(member -> member.character() != null)) {
                            facade.startGame();
                        }
                    }

                    @Override
                    public void onSnapshot(WorldSnapshot update) {
                        snapshot.complete(update);
                    }
                });
                facade.connect(address);
            });
            CompletableFuture.allOf(joined, snapshot).get(12, TimeUnit.SECONDS);
            Welcome welcome = joined.join();
            WorldSnapshot world = snapshot.join();
            System.out.printf("WELCOME: nickname=%s, player=%s, slot=%d%n",
                    welcome.nickname(), welcome.playerId(), welcome.slot());
            System.out.printf("WORLD_SNAPSHOT: tick=%d, arena=%s, players=%d%n",
                    world.tick(), world.arena().arenaType(), world.players().size());
            SwingUtilities.invokeAndWait(() -> {
                facade.sendInput(PlayerInput.NONE);
                facade.disconnect();
            });
        } catch (Exception exception) {
            System.err.println("Facade demo failed: " + exception);
            throw exception;
        } finally {
            SwingUtilities.invokeAndWait(facade::close);
            System.out.println("Facade closed");
        }
    }

    private static void printSelection(String label, GameClientFacade facade) {
        System.out.printf("%s: %s %s, canUndo=%s%n", label,
                facade.selectedCharacter(), facade.stats(), facade.canUndoSelection());
    }
}
