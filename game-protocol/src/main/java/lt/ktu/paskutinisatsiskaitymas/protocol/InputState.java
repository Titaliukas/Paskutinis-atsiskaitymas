package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Complete client input state. The server derives all motion, attacks and collision results. */
public record InputState(long sequence, boolean left, boolean right, boolean jump,
        boolean ranged, boolean melee, boolean examinator, boolean prone) implements Message {
    public InputState(long sequence, boolean left, boolean right, boolean jump, boolean ranged, boolean melee) {
        this(sequence, left, right, jump, ranged, melee, false, false);
    }

    public InputState {
        if (sequence < 0) {
            throw new IllegalArgumentException("Input sequence cannot be negative");
        }
    }
}