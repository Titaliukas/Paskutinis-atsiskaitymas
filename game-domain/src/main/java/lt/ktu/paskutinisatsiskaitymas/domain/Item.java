package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** A world item declaration; pickup, effects and inventory rules are deferred. */
public record Item(UUID id, Position position) {
    public Item {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
    }
}
