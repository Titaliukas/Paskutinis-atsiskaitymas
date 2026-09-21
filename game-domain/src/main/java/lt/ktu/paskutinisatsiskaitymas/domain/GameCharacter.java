package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** A player-controlled world entity. Movement, health and other mechanics are intentionally absent. */
public record GameCharacter(UUID id, UUID playerId, Position position) {
    public GameCharacter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(position, "position");
    }
}
