package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;

/** A live object eligible for a sequential Examinator pass. Rules belong to the target, not its iterator. */
public interface AutoTarget {
    boolean canBeTargetedBy(GameCharacter source);
    Position aimPoint();
    /** Changes when the same object starts a new target lifetime. */
    default long targetLifetime() { return 0; }
    void receiveHit(List<AttackEffect> effects, int direction);
}
