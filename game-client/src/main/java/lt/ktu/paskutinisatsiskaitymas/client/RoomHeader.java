package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyState;

/** Shares the room code between the character picker and waiting room. */
final class RoomHeader extends JPanel {
    private final JLabel codeLabel = new JLabel();
    private final JButton copy = new JButton("Copy code");
    private String code;

    RoomHeader(Consumer<String> status) {
        this(() -> Toolkit.getDefaultToolkit().getSystemClipboard(), status);
    }

    RoomHeader(Supplier<Clipboard> clipboard, Consumer<String> status) {
        super(new FlowLayout(FlowLayout.LEFT, 10, 4));
        codeLabel.setFont(codeLabel.getFont().deriveFont(20f));
        add(codeLabel);
        add(copy);
        copy.setVisible(false);
        copy.addActionListener(event -> {
            if (!copy.isVisible() || code == null) {
                return;
            }
            try {
                clipboard.get().setContents(new StringSelection(code), null);
                status.accept("Room code copied");
            } catch (IllegalStateException | SecurityException | UnsupportedOperationException exception) {
                status.accept("Could not copy room code");
            }
        });
    }

    void showRoom(LobbyState state, UUID localPlayerId) {
        code = state.code();
        codeLabel.setText("Room code: " + code + "   (" + state.members().size() + "/4)");
        copy.setVisible(state.hostId().equals(localPlayerId));
    }
}
