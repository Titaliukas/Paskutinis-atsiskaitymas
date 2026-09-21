package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** An identified platform location, with geometry and collision rules deferred. */
public record Platform(UUID id, Position position) {
    public Platform {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
    }
}
