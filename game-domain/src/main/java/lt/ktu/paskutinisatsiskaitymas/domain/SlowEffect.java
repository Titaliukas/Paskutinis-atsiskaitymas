package lt.ktu.paskutinisatsiskaitymas.domain;

/** Concrete implementor: temporarily reduces movement speed. */
public record SlowEffect(double multiplier, double seconds) implements AttackEffect {
    public SlowEffect {
        if (!Double.isFinite(multiplier) || !Double.isFinite(seconds) || multiplier <= 0 || seconds <= 0) {
            throw new IllegalArgumentException("Slow multiplier and duration must be finite and positive");
        }
    }

    @Override
    public void applyTo(GameCharacter target, int direction) {
        target.applySpeedMultiplier(multiplier, seconds);
    }
}