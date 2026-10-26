package lt.ktu.paskutinisatsiskaitymas.client;

import java.net.URI;
import java.util.Objects;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.StatAllocation;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/**
 * Owns one client's selection, undo history and asynchronous connection.
 * Call operations on the EDT (also in a windowless client); observers run there too.
 * Network callbacks only enqueue work and never wait for the EDT.
 */
public final class GameClientFacade implements AutoCloseable {
    private final CharacterSelection selection = new CharacterSelection();
    private final CommandHistory history = new CommandHistory();
    private final ClientConnection connection = new ClientConnection(new ClientEvents() {
        @Override
        public void onJoined(Welcome welcome) {
            dispatch(history::clear);
        }
    });
    // Mutated on the EDT under the connection monitor, captured under that same monitor by callbacks.
    private long generation;

    public void selectCharacter(String character) {
        requireEdt();
        if (!selection.selectedCharacter().equals(character)) {
            history.execute(new SelectCharacterCommand(selection, character));
        }
    }

    public void changeStat(SelectableStat stat, int value) {
        requireEdt();
        if (Objects.requireNonNull(stat, "stat").value(selection.stats()) != value) {
            history.execute(new ChangeStatCommand(selection, stat, value));
        }
    }

    public void undoSelection() {
        requireEdt();
        history.undo();
    }

    public String selectedCharacter() {
        requireEdt();
        return selection.selectedCharacter();
    }

    public StatAllocation stats() {
        requireEdt();
        return selection.stats();
    }

    public boolean canUndoSelection() {
        requireEdt();
        return history.canUndo();
    }

    public void connect(URI address, String nickname) {
        requireEdt();
        synchronized (connection) {
            generation++;
            connection.connect(address, nickname, selection.selectedCharacter(), selection.stats());
        }
    }

    public void disconnect() {
        requireEdt();
        synchronized (connection) {
            generation++;
            connection.disconnect();
        }
    }

    @Override
    public void close() {
        requireEdt();
        synchronized (connection) {
            generation++;
            connection.close();
        }
    }

    public void sendInput(PlayerInput input) {
        requireEdt();
        connection.sendInput(input);
    }

    public void addObserver(ClientEvents observer) {
        requireEdt();
        Objects.requireNonNull(observer, "observer");
        connection.addObserver(new ClientEvents() {
            private ConnectionStatus previousStatus;

            @Override
            public void onConnectionStatus(ConnectionStatus status) {
                dispatch(() -> {
                    if (!status.equals(previousStatus)) {
                        previousStatus = status;
                        observer.onConnectionStatus(status);
                    }
                });
            }

            @Override
            public void onJoined(Welcome welcome) {
                dispatch(() -> observer.onJoined(welcome));
            }

            @Override
            public void onSnapshot(WorldSnapshot snapshot) {
                dispatch(() -> observer.onSnapshot(snapshot));
            }

            @Override
            public void onGameEvent(GameEventMessage event) {
                dispatch(() -> observer.onGameEvent(event));
            }
        });
    }

    private void dispatch(Runnable event) {
        long attempt = generation;
        Runnable guarded = () -> {
            if (attempt == generation) {
                event.run();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            guarded.run();
        } else {
            SwingUtilities.invokeLater(guarded);
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Use GameClientFacade on the EDT; wait for network results elsewhere");
        }
    }
}
