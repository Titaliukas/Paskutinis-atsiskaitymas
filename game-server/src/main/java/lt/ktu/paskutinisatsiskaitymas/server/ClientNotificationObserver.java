package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.Objects;
import java.util.function.Consumer;
import lt.ktu.paskutinisatsiskaitymas.domain.GameEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.GameEventListener;
import lt.ktu.paskutinisatsiskaitymas.domain.ItemCollectedEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.ItemSpawnedEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.PlayerJoinedEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.PlayerLeftEvent;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;

final class ClientNotificationObserver implements GameEventListener {
    private final Consumer<GameEventMessage> sink;

    ClientNotificationObserver(Consumer<GameEventMessage> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public void onEvent(GameEvent event) {
        GameEventMessage message = toMessage(event);
        if (message != null) {
            sink.accept(message);
        }
    }

    private static GameEventMessage toMessage(GameEvent event) {
        if (event instanceof ItemCollectedEvent e) {
            return new GameEventMessage("ITEM_COLLECTED", e.nickname() + " picked up " + e.itemType());
        }
        if (event instanceof ItemSpawnedEvent e) {
            return new GameEventMessage("ITEM_SPAWNED", e.itemType() + " appeared");
        }
        if (event instanceof PlayerJoinedEvent e) {
            return new GameEventMessage("PLAYER_JOINED", e.nickname() + " joined");
        }
        if (event instanceof PlayerLeftEvent e) {
            return new GameEventMessage("PLAYER_LEFT", e.nickname() + " left");
        }
        return null;
    }
}