package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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
    private final JButton connect = new JButton("Connect");
    private final JButton disconnect = new JButton("Disconnect");
    private final JLabel status = new JLabel("Disconnected");
    private final GamePanel gamePanel = new GamePanel();
    private final ClientConnection connection;
    private final KeyboardInput keyboard;

    ConnectionWindow(String initialAddress, String initialNickname) {
        super("Paskutinis atsiskaitymas — Connection");
        connection = new ClientConnection(new ClientEvents() {
            @Override
            public void onConnectionStatus(ConnectionStatus update) {
                SwingUtilities.invokeLater(() -> showStatus(update));
            }

            @Override
            public void onJoined(Welcome welcome) {
                SwingUtilities.invokeLater(() -> {
                    gamePanel.setLocalPlayerId(welcome.playerId());
                    gamePanel.requestFocusInWindow();
                });
            }

            @Override
            public void onSnapshot(WorldSnapshot snapshot) {
                SwingUtilities.invokeLater(() -> gamePanel.setSnapshot(snapshot));
            }
        });
        keyboard = new KeyboardInput(gamePanel, connection);
        address = new JTextField(initialAddress, 28);
        nickname = new JTextField(initialNickname, 28);
        JPanel fields = new JPanel(new GridLayout(2, 2, 8, 6));
        fields.add(new JLabel("Server address"));
        fields.add(address);
        fields.add(new JLabel("Player nickname"));
        fields.add(nickname);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(connect);
        buttons.add(disconnect);
        JPanel form = new JPanel(new BorderLayout(8, 4));
        form.add(fields, BorderLayout.CENTER);
        form.add(buttons, BorderLayout.EAST);
        JPanel content = new JPanel(new BorderLayout(8, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(form, BorderLayout.NORTH);
        content.add(gamePanel, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        setContentPane(content);
        disconnect.setEnabled(false);
        connect.addActionListener(event -> {
            try {
                connection.connect(URI.create(address.getText().trim()), nickname.getText().trim());
            } catch (IllegalArgumentException exception) {
                showStatus(new ConnectionStatus(ConnectionStatus.State.ERROR, exception.getMessage()));
            }
        });
        disconnect.addActionListener(event -> {
            keyboard.reset();
            connection.disconnect();
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
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
        // A server-provided nickname must not be interpreted as Swing HTML.
        status.putClientProperty("html.disable", Boolean.TRUE);
        status.setText(update.detail());
        if (!active) {
            keyboard.reset();
            gamePanel.clearGame();
        } else if (update.state() == ConnectionStatus.State.CONNECTED) {
            gamePanel.requestFocusInWindow();
        }
    }
}
