package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;

/** A live object eligible for an instant Examinator volley. Rules belong to the target, not its iterator. */
public interface AutoTarget {
    boolean canBeTargetedBy(GameCharacter source);
    Position aimPoint();
    void receiveHit(List<AttackEffect> effects, int direction);
}
