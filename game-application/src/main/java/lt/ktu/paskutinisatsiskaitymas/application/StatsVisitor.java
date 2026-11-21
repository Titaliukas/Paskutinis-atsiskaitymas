package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lt.ktu.paskutinisatsiskaitymas.domain.GameEventVisitor;
import lt.ktu.paskutinisatsiskaitymas.domain.ItemCollectedEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.ItemSpawnedEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.PlayerJoinedEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.PlayerLeftEvent;

/** ConcreteVisitor: kaupia statistiką; rezultato nereikia, todėl Void. */
final class StatsVisitor implements GameEventVisitor<Void> {
    private final Map<UUID, AtomicInteger> collected = new ConcurrentHashMap<>();

    @Override
    public Void visit(ItemSpawnedEvent e) {
        return null;
    }

    @Override
    public Void visit(ItemCollectedEvent e) {
        collected.computeIfAbsent(e.playerId(), id -> new AtomicInteger()).incrementAndGet();
        return null;
    }

    @Override
    public Void visit(PlayerJoinedEvent e) {
        return null;
    }

    @Override
    public Void visit(PlayerLeftEvent e) {
        return null;
    }

    int collectedBy(UUID playerId) {
        AtomicInteger value = collected.get(playerId);
        return value == null ? 0 : value.get();
    }
}