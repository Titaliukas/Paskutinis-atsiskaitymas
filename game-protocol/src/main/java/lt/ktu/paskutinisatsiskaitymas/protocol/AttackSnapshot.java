package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Objects;
import java.util.UUID;

/** Immutable render state for one active attack, expressed as a bounding box. */
public record AttackSnapshot(UUID id, String type, double x, double y, double width, double height) {
    public AttackSnapshot {
        Objects.requireNonNull(id, "id");
        type = WireText.require(type, "type", 32);
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
    }
}