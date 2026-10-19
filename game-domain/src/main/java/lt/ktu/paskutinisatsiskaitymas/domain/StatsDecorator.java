package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;

/**
 * Decorator: one timed effect wrapped around another {@link CharacterStats}.
 * Every query passes through to the inner chain unless a subclass changes it, so effects stack in any order.
 */
public abstract class StatsDecorator implements CharacterStats {
    private CharacterStats inner;
    private double remainingSeconds;

    protected StatsDecorator(CharacterStats inner, double seconds) {
        this.inner = Objects.requireNonNull(inner, "inner");
        if (!Double.isFinite(seconds) || seconds <= 0) {
            throw new IllegalArgumentException("Effect duration must be finite and positive");
        }
        this.remainingSeconds = seconds;
    }

    protected final CharacterStats inner() {
        return inner;
    }

    /** Short layer name used by {@link #describe()}. */
    protected abstract String name();

    @Override
    public double speedMultiplier() {
        return inner.speedMultiplier();
    }

    @Override
    public double jumpMultiplier() {
        return inner.jumpMultiplier();
    }

    @Override
    public boolean shielded() {
        return inner.shielded();
    }

    @Override
    public final CharacterStats tick(double seconds) {
        inner = inner.tick(seconds);
        remainingSeconds -= seconds;
        return remainingSeconds > 0 ? this : inner;
    }

    @Override
    public final CharacterStats without(Class<? extends StatsDecorator> type) {
        inner = inner.without(type);
        return type.isInstance(this) ? inner : this;
    }

    @Override
    public final String describe() {
        return name() + "(" + inner.describe() + ")";
    }
}
