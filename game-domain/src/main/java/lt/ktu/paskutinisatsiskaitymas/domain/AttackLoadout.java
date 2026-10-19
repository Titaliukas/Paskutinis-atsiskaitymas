package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;

/**
 * Bridge configuration of one character: which effects its ranged (J) and melee (K) attacks carry.
 * The delivery classes (ProjectileAttack, MeleeAttack) are shared by every character.
 */
public record AttackLoadout(List<AttackEffect> ranged, List<AttackEffect> melee) {
    public AttackLoadout {
        ranged = List.copyOf(ranged);
        melee = List.copyOf(melee);
        if (ranged.isEmpty() || melee.isEmpty()) {
            throw new IllegalArgumentException("Both attacks need at least one effect");
        }
    }

    public List<AttackEffect> effectsFor(AttackType type) {
        return switch (type) {
            case RANGED -> ranged;
            case MELEE -> melee;
        };
    }
}