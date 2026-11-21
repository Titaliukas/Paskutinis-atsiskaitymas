package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.GameEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.GameEventListener;

public final class StatsObserver implements GameEventListener {
    private final StatsVisitor visitor = new StatsVisitor();

    @Override
    public void onEvent(GameEvent event) {
        event.accept(visitor);
    }

    public int itemsCollectedBy(UUID playerId) {
        return visitor.collectedBy(playerId);
    }
}