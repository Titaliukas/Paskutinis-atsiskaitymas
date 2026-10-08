package lt.ktu.paskutinisatsiskaitymas.domain;

/** Intended velocity and nullable attack target. Decisions cannot mutate characters. */
public record NPCAction(double velocityX, double velocityY, Integer attackTargetId) {
    public NPCAction {
        NPCGeometry.finite(velocityX, "velocityX");
        NPCGeometry.finite(velocityY, "velocityY");
        NPCGeometry.id(attackTargetId);
    }

    public boolean hasAttackRequest() {
        return attackTargetId != null;
    }
}
