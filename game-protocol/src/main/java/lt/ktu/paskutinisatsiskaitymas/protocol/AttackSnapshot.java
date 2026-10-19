package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Objects;
import java.util.UUID;

/** Immutable render state for one active attack, expressed as a bounding box; sourceId is the attacking player. */
public record AttackSnapshot(UUID id, UUID sourceId, String type, double x, double y, double width, double height) {
    public AttackSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");
        type = WireText.require(type, "type", 32);
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
    }
}