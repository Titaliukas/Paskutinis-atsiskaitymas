package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Shared validation for finite transport geometry. */
final class WireNumbers {
    private WireNumbers() { }

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    static double positive(double value, String name) {
        finite(value, name);
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
