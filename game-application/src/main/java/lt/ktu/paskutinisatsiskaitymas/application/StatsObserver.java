package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lt.ktu.paskutinisatsiskaitymas.domain.GameEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.GameEventListener;
import lt.ktu.paskutinisatsiskaitymas.domain.ItemCollectedEvent;

public final class StatsObserver implements GameEventListener {
    private final Map<UUID, AtomicInteger> collected = new ConcurrentHashMap<>();

    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof ItemCollectedEvent collectedEvent) {
            collected.computeIfAbsent(collectedEvent.playerId(), id -> new AtomicInteger())
                    .incrementAndGet();
        }
    }

    public int itemsCollectedBy(UUID playerId) {
        AtomicInteger value = collected.get(playerId);
        return value == null ? 0 : value.get();
    }
}