package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Recorded world-space endpoint; never a client-selected target identity. */
public record ShotEndpoint(double x, double y) {
    public ShotEndpoint {
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
    }
}
