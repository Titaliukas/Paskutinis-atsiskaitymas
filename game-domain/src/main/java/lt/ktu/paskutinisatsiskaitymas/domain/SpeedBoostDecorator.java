package lt.ktu.paskutinisatsiskaitymas.domain;

/** Concrete decorator: multiplies move speed while a speed boost item is active. */
public final class SpeedBoostDecorator extends StatsDecorator {
    private final double multiplier;

    public SpeedBoostDecorator(CharacterStats inner, double multiplier, double seconds) {
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
        return "SpeedBoost";
    }
}
