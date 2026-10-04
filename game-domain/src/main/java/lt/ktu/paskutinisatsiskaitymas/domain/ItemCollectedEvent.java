package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;
import java.util.UUID;

public record ItemCollectedEvent(UUID playerId, String nickname, ItemType itemType) implements GameEvent {
    public ItemCollectedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(nickname, "nickname");
        Objects.requireNonNull(itemType, "itemType");
    }
}