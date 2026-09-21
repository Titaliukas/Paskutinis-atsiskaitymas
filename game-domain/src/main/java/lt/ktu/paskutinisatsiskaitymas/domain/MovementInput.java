package lt.ktu.paskutinisatsiskaitymas.domain;

/** Input intent for one simulation tick; it contains no client-authoritative position or velocity. */
public record MovementInput(boolean left, boolean right, boolean jumpRequested) {
    public static final MovementInput NONE = new MovementInput(false, false, false);
}
