package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable render state for one active attack, expressed as a bounding box. sourceId is the attacking player and
 * direction is the attacker's facing when the attack started (-1 left, 1 right).
 */
public record AttackSnapshot(UUID id, UUID sourceId, String type, int direction,
        double x, double y, double width, double height) {
    public AttackSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");
        type = WireText.require(type, "type", 32);
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("Attack direction must be -1 or 1");
        }
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
    }
}