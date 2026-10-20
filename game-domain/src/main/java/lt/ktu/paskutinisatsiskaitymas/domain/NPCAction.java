package lt.ktu.paskutinisatsiskaitymas.domain;

/** Intended velocity and optional melee or throw target. Decisions cannot mutate characters. */
public record NPCAction(double velocityX, double velocityY, Integer attackTargetId, Integer throwTargetId) {
    public NPCAction(double velocityX, double velocityY, Integer attackTargetId) {
        this(velocityX, velocityY, attackTargetId, null);
    }

    public NPCAction {
        NPCGeometry.finite(velocityX, "velocityX");
        NPCGeometry.finite(velocityY, "velocityY");
        NPCGeometry.id(attackTargetId);
        NPCGeometry.id(throwTargetId);
        if (attackTargetId != null && throwTargetId != null) {
            throw new IllegalArgumentException("An action cannot request both melee and throwing");
        }
    }

    public boolean hasAttackRequest() {
        return attackTargetId != null;
    }

    public boolean hasThrowRequest() { return throwTargetId != null; }
}
