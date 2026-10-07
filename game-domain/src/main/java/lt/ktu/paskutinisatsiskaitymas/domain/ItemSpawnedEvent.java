package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;
import java.util.UUID;

public record ItemSpawnedEvent(UUID itemId, ItemType itemType) implements GameEvent {
    public ItemSpawnedEvent {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(itemType, "itemType");
    }
}