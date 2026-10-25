package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Transport-only paired edge opening geometry, in the same world coordinates as platforms. */
public record SidePassageSnapshot(double openingTop, double supportY) {
    public SidePassageSnapshot {
        WireNumbers.finite(openingTop, "openingTop");
        WireNumbers.positive(supportY, "supportY");
        if (openingTop < 0 || openingTop >= supportY) {
            throw new IllegalArgumentException("Passage top must be above its support");
        }
    }
}
