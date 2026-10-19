package lt.ktu.paskutinisatsiskaitymas.domain;

/** Concrete decorator: multiplies jump height while a jump boost item is active. */
public final class JumpBoostDecorator extends StatsDecorator {
    private final double multiplier;

    public JumpBoostDecorator(CharacterStats inner, double multiplier, double seconds) {
        super(inner, seconds);
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            throw new IllegalArgumentException("Multiplier must be finite and positive");
        }
        this.multiplier = multiplier;
    }

    @Override
    public double jumpMultiplier() {
        return inner().jumpMultiplier() * multiplier;
    }

    @Override
    protected String name() {
        return "JumpBoost";
    }
}
