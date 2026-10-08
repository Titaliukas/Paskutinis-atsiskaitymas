package lt.ktu.paskutinisatsiskaitymas.domain;

public final class FleeStrategy implements NPCActivityStrategy {
    private final double speedMultiplier;

    public FleeStrategy(double speedMultiplier) {
        this.speedMultiplier = NPCGeometry.positive(speedMultiplier, "speedMultiplier");
    }

    @Override
    public NPCAction decide(NPCState self, NPCTarget target, double seconds) {
        NPCGeometry.positive(seconds, "seconds");
        // STRATEGY-REPORT R3d BEGIN: Compute away-from motion from immutable target geometry.
        if (!NPCGeometry.matches(self, target)) {
            return new NPCAction(0, self.velocityY(), null);
        }
        double toward = NPCGeometry.direction(self, target);
        if (toward == 0) {
            toward = self.facingRight() ? 1 : -1;
        }
        return new NPCAction(-toward * self.speed() * speedMultiplier, self.velocityY(), null);
        // STRATEGY-REPORT R3d END
    }
}
