package lt.ktu.paskutinisatsiskaitymas.domain;

/** Immutable world-space coordinates; units and coordinate conventions belong to the next gameplay slice. */
public record Position(double x, double y) {
    public Position {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Coordinates must be finite");
        }
    }
}
