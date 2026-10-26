package lt.ktu.paskutinisatsiskaitymas.client;

import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;

/** Receives facade events on the EDT. */
final class NotificationObserver implements ClientEvents {
    private final GamePanel panel;

    NotificationObserver(GamePanel panel) {
        this.panel = panel;
    }

    @Override
    public void onGameEvent(GameEventMessage event) {
        panel.showNotification(event.text());
    }
}
