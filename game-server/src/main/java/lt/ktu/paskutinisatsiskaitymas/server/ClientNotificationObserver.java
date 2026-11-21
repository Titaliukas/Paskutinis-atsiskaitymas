package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.Objects;
import java.util.function.Consumer;
import lt.ktu.paskutinisatsiskaitymas.domain.GameEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.GameEventListener;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;

final class ClientNotificationObserver implements GameEventListener {
    private final Consumer<GameEventMessage> sink;
    private final NotificationVisitor visitor = new NotificationVisitor();

    ClientNotificationObserver(Consumer<GameEventMessage> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public void onEvent(GameEvent event) {
        sink.accept(event.accept(visitor));
    }
}