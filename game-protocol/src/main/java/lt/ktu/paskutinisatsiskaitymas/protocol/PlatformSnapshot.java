package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Immutable transport geometry for one platform. */
public record PlatformSnapshot(double x, double y, double width, double height) {
    public PlatformSnapshot {
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
    }
}
