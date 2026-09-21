package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Shared wire-field validation; deliberately contains no domain rules. */
final class WireText {
    private WireText() { }

    static String require(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maximumLength + " characters");
        }
        return value;
    }
}
