package lt.ktu.paskutinisatsiskaitymas.domain;

public final class ChaseStrategy implements NPCActivityStrategy {
    private final double speedMultiplier;

    public ChaseStrategy(double speedMultiplier) {
        this.speedMultiplier = NPCGeometry.positive(speedMultiplier, "speedMultiplier");
    }

    @Override
    public NPCAction decide(NPCState self, NPCTarget target, double seconds) {
        NPCGeometry.positive(seconds, "seconds");
        // STRATEGY-REPORT R3b BEGIN: Compute toward motion from immutable target geometry.
        if (!NPCGeometry.matches(self, target)) {
            return new NPCAction(0, self.velocityY(), null);
        }
        return new NPCAction(NPCGeometry.direction(self, target) * self.speed() * speedMultiplier, self.velocityY(), null);
        // STRATEGY-REPORT R3b END
    }
}
