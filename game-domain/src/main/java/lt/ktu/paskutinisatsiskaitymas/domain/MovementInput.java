package lt.ktu.paskutinisatsiskaitymas.domain;

/** Input intent for one simulation tick; it contains no client-authoritative position or velocity. */
public record MovementInput(boolean left, boolean right, boolean jumpRequested, boolean prone) {
    public MovementInput(boolean left, boolean right, boolean jumpRequested) {
        this(left, right, jumpRequested, false);
    }

    public static final MovementInput NONE = new MovementInput(false, false, false);
}
