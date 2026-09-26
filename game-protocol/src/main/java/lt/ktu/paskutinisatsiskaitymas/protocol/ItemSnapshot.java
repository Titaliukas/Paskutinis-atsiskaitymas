package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Objects;
import java.util.UUID;

/** Immutable render state for one item lying in the arena, waiting to be collected. */
public record ItemSnapshot(UUID id, String type, double x, double y) {
    public ItemSnapshot {
        Objects.requireNonNull(id, "id");
        type = WireText.require(type, "type", 32);
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
    }
}