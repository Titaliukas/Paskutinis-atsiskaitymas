package lt.ktu.paskutinisatsiskaitymas.domain;

/** Concrete decorator: multiplies move speed while a slowing attack is active. Separate from the speed boost so both stack. */
public final class SlowDecorator extends StatsDecorator {
    private final double multiplier;

    public SlowDecorator(CharacterStats inner, double multiplier, double seconds) {
        super(inner, seconds);
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            throw new IllegalArgumentException("Multiplier must be finite and positive");
        }
        this.multiplier = multiplier;
    }

    @Override
    public double speedMultiplier() {
        return inner().speedMultiplier() * multiplier;
    }

    @Override
    protected String name() {
        return "Slow";
    }
}
