package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;
import java.util.UUID;

/** Immutable view of an active attack as an axis-aligned box, suitable for mapping to a transport DTO. */
public record AttackState(UUID id, UUID sourceId, AttackType type, int direction,
        double x, double y, double width, double height) {
    public AttackState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(type, "type");
    }
}