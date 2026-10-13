package lt.ktu.paskutinisatsiskaitymas.domain;

public final class AttackStrategy implements NPCActivityStrategy {
    @Override
    public NPCAction decide(NPCState self, NPCTarget target, double seconds) {
        NPCGeometry.positive(seconds, "seconds");
        // STRATEGY-REPORT R3c BEGIN: Request an attack; the session alone validates and damages.
        Integer request = NPCGeometry.matches(self, target) && self.attackReady()
                && NPCGeometry.distance(self, target) <= self.attackRange() ? target.id() : null;
        return new NPCAction(0, self.velocityY(), request);
        // STRATEGY-REPORT R3c END
    }
}
