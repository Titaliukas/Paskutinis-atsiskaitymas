package lt.ktu.paskutinisatsiskaitymas.client;

import com.studiohartman.jamepad.ControllerManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** EDT-only connection form surrounding a stable Java2D game surface. */
final class ConnectionWindow extends JFrame {
    private final JTextField address;
    private final JTextField nickname;
    private final CharacterSelectionPanel characterPicker = new CharacterSelectionPanel();
    private final CharacterSelection selection = new CharacterSelection();
    private final CommandHistory selectionHistory = new CommandHistory();
    private final JButton undoSelection = new JButton("Undo change");
    private final JButton connect = new JButton("Connect");
    private final JButton disconnect = new JButton("Disconnect");
    private final JLabel status = new JLabel("Disconnected");
    private final GamePanel gamePanel = new GamePanel();
    private final CardLayout screenLayout = new CardLayout();
    private final JPanel screens = new JPanel(screenLayout);
    private final ClientConnection connection;
    private final JComboBox<InputDevice> inputDevice = new JComboBox<>();
    /** Shared SDL controller access, or null when the native library could not be loaded. */
    private final ControllerManager controllers = createControllerManager();
    private InputSource input;

    ConnectionWindow(String initialAddress, String initialNickname) {
        super("Paskutinis atsiskaitymas — Connection");
        setIconImage(AssetManager.getInstance().getImage("PATASIJUS"));
        connection = new ClientConnection(new ClientEvents() {
            @Override
            public void onConnectionStatus(ConnectionStatus update) {
                if (SwingUtilities.isEventDispatchThread()) {
                    showStatus(update);
                } else {
                    SwingUtilities.invokeLater(() -> showStatus(update));
                }
            }

            @Override
            public void onJoined(Welcome welcome) {
                SwingUtilities.invokeLater(() -> {
                    selectionHistory.clear();
                    updateUndoSelection();
                    gamePanel.setLocalPlayerId(welcome.playerId());
                    gamePanel.requestFocusInWindow();
                });
            }

            @Override
            public void onSnapshot(WorldSnapshot snapshot) {
                SwingUtilities.invokeLater(() -> gamePanel.setSnapshot(snapshot));
            }
        });
        input = InputDevice.KEYBOARD.create(gamePanel, controllers);
        input.setListener(connection::sendInput);
        for (InputDevice device : InputDevice.values()) {
            if (!device.gamepad() || controllers != null) {
                inputDevice.addItem(device);
            }
        }
        inputDevice.setToolTipText(controllers != null ? InputDevice.KEYBOARD.controlsHint()
                : "Controller support is unavailable on this system; the keyboard still works");
        inputDevice.addActionListener(event -> switchInput((InputDevice) inputDevice.getSelectedItem()));
        connection.addObserver(new NotificationObserver(gamePanel));
        address = new JTextField(initialAddress, 28);
        nickname = new JTextField(initialNickname, 28);
        JPanel fields = new JPanel(new GridLayout(3, 2, 8, 6));
        fields.add(new JLabel("Server address"));
        fields.add(address);
        fields.add(new JLabel("Player nickname"));
        fields.add(nickname);
        fields.add(new JLabel("Input"));
        fields.add(inputDevice);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(undoSelection);
        buttons.add(connect);
        buttons.add(disconnect);
        JPanel form = new JPanel(new BorderLayout(8, 4));
        form.add(fields, BorderLayout.CENTER);
        form.add(buttons, BorderLayout.EAST);
        JPanel content = new JPanel(new BorderLayout(8, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(form, BorderLayout.NORTH);
        screens.add(characterPicker, "selection");
        screens.add(gamePanel, "game");
        content.add(screens, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        setContentPane(content);
        disconnect.setEnabled(false);
        characterPicker.setSelectedCharacter(selection.selectedCharacter());
        undoSelection.setEnabled(false);
        characterPicker.setSelectionListener(selected -> {
            if (!selection.selectedCharacter().equals(selected)) {
                selectionHistory.execute(new SelectCharacterCommand(selection, selected));
                characterPicker.setStats(selection.stats());
                updateUndoSelection();
            }
        });
        characterPicker.setStatChangeListener((stat, value) -> {
            if (stat.value(selection.stats()) != value) {
                selectionHistory.execute(new ChangeStatCommand(selection, stat, value));
                characterPicker.setStats(selection.stats());
                updateUndoSelection();
            }
        });
        undoSelection.addActionListener(event -> {
            selectionHistory.undo();
            characterPicker.setSelectedCharacter(selection.selectedCharacter());
            characterPicker.setStats(selection.stats());
            updateUndoSelection();
        });
        connect.addActionListener(event -> {
            try {
                connection.connect(URI.create(address.getText().trim()), nickname.getText().trim(),
                        selection.selectedCharacter(), selection.stats());
            } catch (IllegalArgumentException exception) {
                showStatus(new ConnectionStatus(ConnectionStatus.State.ERROR, exception.getMessage()));
            }
        });
        disconnect.addActionListener(event -> {
            input.reset();
            connection.disconnect();
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                input.close();
                if (controllers != null) {
                    controllers.quitSDLGamepad();
                }
                connection.close();
            }
        });
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new java.awt.Dimension(720, 500));
        setLocationByPlatform(true);
    }

    private void showStatus(ConnectionStatus update) {
        boolean active = update.state() == ConnectionStatus.State.CONNECTING
                || update.state() == ConnectionStatus.State.CONNECTED;
        address.setEnabled(!active);
        nickname.setEnabled(!active);
        connect.setEnabled(!active);
        disconnect.setEnabled(active);
        characterPicker.setEnabled(!active);
        screenLayout.show(screens, update.state() == ConnectionStatus.State.CONNECTED ? "game" : "selection");
        updateUndoSelection();
        // A server-provided nickname must not be interpreted as Swing HTML.
        status.putClientProperty("html.disable", Boolean.TRUE);
        status.setText(update.detail());
        if (!active) {
            input.reset();
            gamePanel.clearGame();
        } else if (update.state() == ConnectionStatus.State.CONNECTED) {
            gamePanel.requestFocusInWindow();
        }
    }

    private void updateUndoSelection() {
        undoSelection.setEnabled(characterPicker.isEnabled() && selectionHistory.canUndo());
    }

    /** Swaps the adapter. Everything else keeps talking to InputSource, so nothing else changes. */
    private void switchInput(InputDevice device) {
        input.reset(); // release anything held on the old device so the character does not keep moving
        input.close();
        input = device.create(gamePanel, controllers);
        input.setListener(connection::sendInput);
        inputDevice.setToolTipText(device.controlsHint());
        status.putClientProperty("html.disable", Boolean.TRUE);
        status.setText(device.controlsHint());
        gamePanel.requestFocusInWindow();
    }

    /** Loads SDL for controllers; returns null if the native library is unavailable so the keyboard still works. */
    private static ControllerManager createControllerManager() {
        try {
            ControllerManager manager = new ControllerManager();
            manager.initSDLGamepad();
            return manager;
        } catch (RuntimeException | LinkageError exception) {
            System.getLogger(ConnectionWindow.class.getName())
                    .log(System.Logger.Level.WARNING, "Controller support unavailable", exception);
            return null;
        }
    }
}
