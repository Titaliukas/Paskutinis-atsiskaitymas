package lt.ktu.paskutinisatsiskaitymas.domain;

/** Per-NPC patrol direction; boundaries refer to the body's left edge in world units. */
public final class PatrolStrategy implements NPCActivityStrategy {
    private final double leftBoundary;
    private final double rightBoundary;
    private boolean movingRight = true;

    public PatrolStrategy(double leftBoundary, double rightBoundary) {
        this.leftBoundary = NPCGeometry.finite(leftBoundary, "leftBoundary");
        this.rightBoundary = NPCGeometry.finite(rightBoundary, "rightBoundary");
        if (leftBoundary < 0 || rightBoundary <= leftBoundary) {
            throw new IllegalArgumentException("Patrol boundaries must be ordered and nonnegative");
        }
    }

    @Override
    public NPCAction decide(NPCState self, NPCTarget target, double seconds) {
        NPCGeometry.positive(seconds, "seconds");
        // STRATEGY-REPORT R3a BEGIN: Reverse at an edge; cap this tick's travel to the boundary.
        if (self.preciseX() >= rightBoundary) {
            movingRight = false;
        } else if (self.preciseX() <= leftBoundary) {
            movingRight = true;
        }
        double remaining = movingRight ? rightBoundary - self.preciseX() : self.preciseX() - leftBoundary;
        double speed = Math.min(self.speed(), Math.max(0, remaining) / seconds);
        return new NPCAction(self.alive() ? speed * (movingRight ? 1 : -1) : 0, self.velocityY(), null);
        // STRATEGY-REPORT R3a END
    }
}
