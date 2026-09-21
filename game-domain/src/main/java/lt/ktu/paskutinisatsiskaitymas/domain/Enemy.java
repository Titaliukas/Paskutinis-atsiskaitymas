package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** A non-player world entity declaration. No spawning or NPC behavior exists yet. */
public record Enemy(UUID id, Position position) {
    public Enemy {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
    }
}
