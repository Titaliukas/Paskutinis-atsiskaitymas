package lt.ktu.paskutinisatsiskaitymas.domain;

/**
 * Decorator component: a character's current movement and protection modifiers.
 * Temporary effects wrap the chain in {@link StatsDecorator} layers that combine with every layer below them.
 * Chains are game-loop-thread confined.
 */
public interface CharacterStats {
    /** Factor applied to the base move speed. */
    double speedMultiplier();

    /** Factor applied to the base jump height. */
    double jumpMultiplier();

    /** True while any layer blocks incoming damage. */
    boolean shielded();

    /** Counts every layer's timer down and returns the chain without the layers that expired. */
    CharacterStats tick(double seconds);

    /** Returns the chain without any layer of the given decorator type, so re-applying an effect refreshes it. */
    CharacterStats without(Class<? extends StatsDecorator> type);

    /** Readable chain, outermost layer first, for example {@code Shield(JumpBoost(SpeedBoost(Base)))}. */
    String describe();
}
