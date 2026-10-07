package lt.ktu.paskutinisatsiskaitymas.client;

import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;

final class NotificationObserver implements ClientEvents {
    private final GamePanel panel;

    NotificationObserver(GamePanel panel) {
        this.panel = panel;
    }

    @Override
    public void onGameEvent(GameEventMessage event) {
        SwingUtilities.invokeLater(() -> panel.showNotification(event.text()));
    }
}