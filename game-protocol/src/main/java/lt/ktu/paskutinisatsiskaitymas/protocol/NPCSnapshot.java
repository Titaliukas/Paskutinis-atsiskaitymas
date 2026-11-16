package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Set;

/** Protocol-only authoritative NPC geometry and presentation facts; activity is a validated wire name. */
public record NPCSnapshot(int id, double x, double y, double width, double height,
        String activity, boolean facingRight, long attackAttemptSequence, boolean stunned, boolean alive) {
    private static final Set<String> ACTIVITIES = Set.of("PATROL", "CHASE", "ATTACK", "FLEE");

    /** A living NPC; the common case. */
    public NPCSnapshot(int id, double x, double y, double width, double height,
            String activity, boolean facingRight, long attackAttemptSequence, boolean stunned) {
        this(id, x, y, width, height, activity, facingRight, attackAttemptSequence, stunned, true);
    }

    public NPCSnapshot {
        if (id <= 0 || attackAttemptSequence < 0) {
            throw new IllegalArgumentException("NPC identity must be positive and sequence nonnegative");
        }
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
        WireNumbers.finite(x + width, "right");
        WireNumbers.finite(y + height, "bottom");
        if (!ACTIVITIES.contains(WireText.require(activity, "activity", 16))) {
            throw new IllegalArgumentException("Unknown NPC activity");
        }
    }
}
