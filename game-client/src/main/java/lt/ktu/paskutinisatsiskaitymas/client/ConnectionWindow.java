package lt.ktu.paskutinisatsiskaitymas.client;

import com.studiohartman.jamepad.ControllerManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyState;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyList;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** EDT-only connection form surrounding a stable Java2D game surface. */
final class ConnectionWindow extends JFrame {
    private final JTextField address;
    private final JTextField nickname;
    private final CharacterSelectionPanel characterPicker = new CharacterSelectionPanel();
    private final GameClientFacade facade = new GameClientFacade();
    private final JButton undoSelection = new JButton("Undo change");
    private final JButton confirm = new JButton("Confirm character");
    private final JButton connect = new JButton("Connect");
    private final JButton disconnect = new JButton("Disconnect");
    private final JButton leave = new JButton("Leave room");
    private final JLabel status = new JLabel("Disconnected");
    private final RoomHeader roomHeader = new RoomHeader(status::setText);
    private final RoomBrowserPanel rooms;
    private final LobbyPanel lobbyPanel = new LobbyPanel(facade::startGame);
    private UUID localPlayerId;
    private ConnectionStatus.State phase = ConnectionStatus.State.DISCONNECTED;
    private boolean busy;
    private final GamePanel gamePanel = new GamePanel(ParticleStatsSink.consoleAndCsv());
    private final CardLayout screenLayout = new CardLayout();
    private final JPanel screens = new JPanel(screenLayout);
    private final GameWindowSizing gameWindowSizing = new GameWindowSizing();
    private final JComboBox<InputDevice> inputDevice = new JComboBox<>();
    /** Shared SDL controller access, or null when the native library could not be loaded. */
    private final ControllerManager controllers = createControllerManager();
    private InputSource input;

    ConnectionWindow(String initialAddress, String initialNickname) {
        super("Paskutinis atsiskaitymas — Connection");
        setIconImage(AssetManager.getInstance().getImage("PATASIJUS"));
        facade.addObserver(new ClientEvents() {
            @Override
            public void onConnectionStatus(ConnectionStatus update) {
                showStatus(update);
            }

            @Override
            public void onJoined(Welcome welcome) {
                localPlayerId = welcome.playerId();
                updateUndoSelection();
                gamePanel.setLocalPlayerId(welcome.playerId());
                gamePanel.requestFocusInWindow();
            }

            @Override
            public void onLobbyState(LobbyState state) {
                roomHeader.showRoom(state, localPlayerId);
                lobbyPanel.showLobby(state, localPlayerId);
            }

            @Override
            public void onLobbyList(LobbyList page) {
                rooms.showPage(page);
            }

            @Override
            public void onRequestPending(boolean pending) {
                busy = pending;
                updateControls();
            }

            @Override
            public void onRequestError(ErrorMessage error) {
                status.setText(error.message());
                if (phase == ConnectionStatus.State.BROWSING && error.code().startsWith("LOBBY_")) {
                    rooms.resetPages();
                }
            }

            @Override
            public void onSnapshot(WorldSnapshot snapshot) {
                gamePanel.setSnapshot(snapshot);
            }
        });
        input = InputDevice.KEYBOARD.create(gamePanel, controllers);
        input.setListener(facade::sendInput);
        for (InputDevice device : InputDevice.values()) {
            if (!device.gamepad() || controllers != null) {
                inputDevice.addItem(device);
            }
        }
        inputDevice.setToolTipText(controllers != null ? InputDevice.KEYBOARD.controlsHint()
                : "Controller support is unavailable on this system; the keyboard still works");
        inputDevice.addActionListener(event -> switchInput((InputDevice) inputDevice.getSelectedItem()));
        facade.addObserver(new NotificationObserver(gamePanel));
        address = new JTextField(initialAddress, 28);
        nickname = new JTextField(initialNickname, 28);
        rooms = new RoomBrowserPanel(
                () -> roomAction(() -> facade.createLobby(nickname.getText())),
                code -> roomAction(() -> facade.joinLobby(nickname.getText(), code)), facade::listLobbies);
        JPanel fields = new JPanel(new GridLayout(3, 2, 8, 6));
        fields.add(new JLabel("Server address"));
        fields.add(address);
        fields.add(new JLabel("Player nickname"));
        fields.add(nickname);
        fields.add(new JLabel("Input"));
        fields.add(inputDevice);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(connect);
        buttons.add(disconnect);
        buttons.add(leave);
        JPanel form = new JPanel(new BorderLayout(8, 4));
        form.add(fields, BorderLayout.CENTER);
        form.add(buttons, BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout(8, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel header = new JPanel(new BorderLayout());
        header.add(form, BorderLayout.NORTH);
        header.add(roomHeader, BorderLayout.SOUTH);
        roomHeader.setVisible(false);
        content.add(header, BorderLayout.NORTH);

        JPanel connectionScreen = new JPanel(new BorderLayout());
        JLabel connectionHint = new JLabel("Connect to a server to browse available rooms.", JLabel.CENTER);
        connectionScreen.add(connectionHint, BorderLayout.CENTER);
        screens.add(connectionScreen, "connection");
        screens.add(rooms, "rooms");
        JPanel selectionScreen = new JPanel(new BorderLayout());
        selectionScreen.add(characterPicker, BorderLayout.CENTER);
        JPanel selectionActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        selectionActions.add(undoSelection);
        selectionActions.add(confirm);
        selectionScreen.add(selectionActions, BorderLayout.SOUTH);
        screens.add(selectionScreen, "selection");
        screens.add(gamePanel, "game");
        screens.add(lobbyPanel, "lobby");
        content.add(screens, BorderLayout.CENTER);
        status.putClientProperty("html.disable", Boolean.TRUE);
        content.add(status, BorderLayout.SOUTH);
        setContentPane(content);
        updateControls();
        characterPicker.setSelectedCharacter(facade.selectedCharacter());
        undoSelection.setEnabled(false);
        characterPicker.setSelectionListener(selected -> {
            facade.selectCharacter(selected);
            characterPicker.setStats(facade.stats());
            updateUndoSelection();
        });
        characterPicker.setStatChangeListener((stat, value) -> {
            facade.changeStat(stat, value);
            characterPicker.setStats(facade.stats());
            updateUndoSelection();
        });
        undoSelection.addActionListener(event -> {
            facade.undoSelection();
            characterPicker.setSelectedCharacter(facade.selectedCharacter());
            characterPicker.setStats(facade.stats());
            updateUndoSelection();
        });
        connect.addActionListener(event ->
                roomAction(() -> facade.connect(URI.create(address.getText().trim()))));
        confirm.addActionListener(event -> roomAction(facade::confirmCharacter));
        disconnect.addActionListener(event -> facade.disconnect());
        leave.addActionListener(event -> facade.leaveLobby());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                input.close();
                if (controllers != null) {
                    controllers.quitSDLGamepad();
                }
                gamePanel.clearGame();
                facade.close();
            }
        });
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(720, 500));
        setLocationByPlatform(true);
    }

    private void roomAction(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            status.setText(exception.getMessage());
        }
    }

    private void showStatus(ConnectionStatus update) {
        phase = update.state();
        switch (phase) {
            case BROWSING -> {
                localPlayerId = null;
                input.reset();
                gamePanel.clearGame();
                rooms.resetPages();
                screenLayout.show(screens, "rooms");
            }
            case SELECTING -> screenLayout.show(screens, "selection");
            case LOBBY -> screenLayout.show(screens, "lobby");
            case CONNECTED -> {
                screenLayout.show(screens, "game");
                roomHeader.setVisible(false);
                if (gameWindowSizing.onStatus(phase)) {
                    sizeForGame();
                }
                gamePanel.requestFocusInWindow();
            }
            case DISCONNECTED, ERROR, CONNECTING -> {
                localPlayerId = null;
                input.reset();
                gamePanel.clearGame();
                screenLayout.show(screens, "connection");
            }
        }
        status.setText(update.detail());
        updateControls();
    }

    private void updateControls() {
        boolean offline = phase == ConnectionStatus.State.DISCONNECTED || phase == ConnectionStatus.State.ERROR;
        boolean choosing = phase == ConnectionStatus.State.SELECTING;
        boolean inRoom = choosing || phase == ConnectionStatus.State.LOBBY || phase == ConnectionStatus.State.CONNECTED;
        address.setEnabled(offline);
        nickname.setEnabled(offline || phase == ConnectionStatus.State.BROWSING && !busy);
        connect.setVisible(offline);
        disconnect.setVisible(phase == ConnectionStatus.State.CONNECTING || phase == ConnectionStatus.State.BROWSING);
        leave.setVisible(inRoom);
        leave.setEnabled(!busy);
        roomHeader.setVisible(choosing || phase == ConnectionStatus.State.LOBBY);
        rooms.setBusy(busy);
        lobbyPanel.setBusy(busy);
        characterPicker.setEnabled(choosing && !busy);
        confirm.setEnabled(choosing && !busy);
        updateUndoSelection();
    }

    private void sizeForGame() {
        // Validate the visible card first. pack() would still include the form and both cards' preferences.
        validate();
        Dimension overhead = new Dimension(getWidth() - gamePanel.getWidth(),
                getHeight() - gamePanel.getHeight());
        var configuration = getGraphicsConfiguration();
        Rectangle workArea = new Rectangle(configuration.getBounds());
        Insets reserved = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        workArea.x += reserved.left;
        workArea.y += reserved.top;
        workArea.width -= reserved.left + reserved.right;
        workArea.height -= reserved.top + reserved.bottom;
        Rectangle target = GameWindowSizing.bounds(getBounds(), workArea, overhead);
        // A fixed minimum must not override the proportional fit on a smaller monitor.
        Dimension minimum = getMinimumSize();
        setMinimumSize(new Dimension(Math.min(minimum.width, target.width), Math.min(minimum.height, target.height)));
        setBounds(target);
        validate();
    }

    private void updateUndoSelection() {
        undoSelection.setEnabled(characterPicker.isEnabled() && facade.canUndoSelection());
    }

    /** Swaps the adapter. Everything else keeps talking to InputSource, so nothing else changes. */
    private void switchInput(InputDevice device) {
        input.reset(); // release anything held on the old device so the character does not keep moving
        input.close();
        input = device.create(gamePanel, controllers);
        input.setListener(facade::sendInput);
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
