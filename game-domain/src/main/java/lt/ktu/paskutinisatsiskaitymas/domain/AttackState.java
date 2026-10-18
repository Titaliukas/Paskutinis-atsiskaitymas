package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;
import java.util.UUID;

/** Immutable view of an active attack as an axis-aligned box, suitable for mapping to a transport DTO. */
public record AttackState(UUID id, AttackType type, double x, double y, double width, double height) {
    public AttackState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}