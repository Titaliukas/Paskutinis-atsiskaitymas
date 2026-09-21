package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.BorderLayout;
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

/** EDT-only connection form. Future Java2D rendering and gameplay input belong in separate panels. */
final class ConnectionWindow extends JFrame {
    private final JTextField address;
    private final JTextField nickname;
    private final JButton connect = new JButton("Connect");
    private final JButton disconnect = new JButton("Disconnect");
    private final JLabel status = new JLabel("Disconnected");
    private final ClientConnection connection = new ClientConnection(update ->
            SwingUtilities.invokeLater(() -> showStatus(update)));

    ConnectionWindow(String initialAddress, String initialNickname) {
        super("Paskutinis atsiskaitymas — Connection");
        address = new JTextField(initialAddress, 28);
        nickname = new JTextField(initialNickname, 28);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Server address"));
        form.add(address);
        form.add(new JLabel("Player nickname"));
        form.add(nickname);
        form.add(connect);
        form.add(disconnect);
        JPanel content = new JPanel(new BorderLayout(8, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(form, BorderLayout.CENTER);
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
        disconnect.addActionListener(event -> connection.disconnect());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                connection.close();
            }
        });
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(getSize());
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
    }
}
