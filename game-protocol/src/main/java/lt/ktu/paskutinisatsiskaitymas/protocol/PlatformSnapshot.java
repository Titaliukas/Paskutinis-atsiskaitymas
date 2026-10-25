package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Set;

/** Immutable transport geometry for one platform. */
public record PlatformSnapshot(double x, double y, double width, double height, String kind) {
    public PlatformSnapshot(double x, double y, double width, double height) {
        this(x, y, width, height, "standard");
    }

    public PlatformSnapshot {
        if (kind == null || !Set.of("standard", "service").contains(kind)) {
            throw new IllegalArgumentException("Platform kind must be standard or service");
        }
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
    }
}
