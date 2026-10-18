package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Objects;
import java.util.UUID;

/** Complete client input state; sequence numbers discard delayed or replayed updates. */
public record InputCommand(
        UUID playerId,
        long sequence,
        boolean left,
        boolean right,
        boolean jump,
        boolean ranged,
        boolean melee) implements GameCommand {
    public InputCommand {
        Objects.requireNonNull(playerId, "playerId");
        if (sequence < 0) {
            throw new IllegalArgumentException("Input sequence cannot be negative");
        }
    }

    /** Movement-only input, without attacks. */
    public InputCommand(UUID playerId, long sequence, boolean left, boolean right, boolean jump) {
        this(playerId, sequence, left, right, jump, false, false);
    }
}