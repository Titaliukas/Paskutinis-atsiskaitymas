package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Complete client input state. The server derives all motion, attacks and collision results. */
public record InputState(long sequence, boolean left, boolean right, boolean jump,
        boolean ranged, boolean melee) implements Message {
    public InputState {
        if (sequence < 0) {
            throw new IllegalArgumentException("Input sequence cannot be negative");
        }
    }
}