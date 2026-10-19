package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.Optional;

/** Selectable characters. Several players may choose the same one. */
public enum CharacterType {
    PATASIJUS(new AttackLoadout(
            List.of(new DamageEffect(12)),
            List.of(new KnockbackEffect(GameConstants.MELEE_KNOCKBACK_X, GameConstants.MELEE_KNOCKBACK_Y),
                    new DamageEffect(32)))),
    VACIJUS(new AttackLoadout(
            List.of(new DamageEffect(30)),
            List.of(new DamageEffect(12)))),
    JURGELINIS(new AttackLoadout(
            List.of(new SlowEffect(0.3, 4.0), new DamageEffect(16)),
            List.of(new KnockbackEffect(450, -200), new DamageEffect(16)))),
    GOGIS(new AttackLoadout(
            List.of(new DamageEffect(20), new SlowEffect(0.65, 2.0)),
            List.of(new KnockbackEffect(500, -220), new DamageEffect(22))));

    private final AttackLoadout loadout;

    CharacterType(AttackLoadout loadout) {
        this.loadout = loadout;
    }

    public AttackLoadout loadout() {
        return loadout;
    }

    /** Case-sensitive wire name lookup; empty for null or unknown names. */
    public static Optional<CharacterType> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (CharacterType type : values()) {
            if (type.name().equals(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}