package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** An identified attack associated with a future source entity; no damage or timing rules. */
public record Attack(UUID id, UUID sourceId) {
    public Attack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");
    }
}
