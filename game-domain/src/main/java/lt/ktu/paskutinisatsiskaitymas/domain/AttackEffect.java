package lt.ktu.paskutinisatsiskaitymas.domain;

/**
 * Bridge implementor: WHAT an attack does to a hit target.
 * The direction (-1 or 1) is the attacker's facing, so directional effects can mirror themselves.
 * Effects that make no sense for NPCs keep the default no-op, so new target kinds never force
 * changes in the attack classes.
 */
public interface AttackEffect {
    void applyTo(GameCharacter target, int direction);
    default void applyTo(NPC target, int direction) { }
}