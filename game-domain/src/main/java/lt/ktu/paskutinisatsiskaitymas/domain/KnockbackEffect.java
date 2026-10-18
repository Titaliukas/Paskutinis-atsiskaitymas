package lt.ktu.paskutinisatsiskaitymas.domain;

/** Concrete implementor: pushes the target away from the attacker. impulseX is mirrored by direction. */
public record KnockbackEffect(double impulseX, double impulseY) implements AttackEffect {
    public KnockbackEffect {
        if (!Double.isFinite(impulseX) || !Double.isFinite(impulseY)) {
            throw new IllegalArgumentException("Impulse must be finite");
        }
    }

    @Override
    public void applyTo(GameCharacter target, int direction) {
        target.applyImpulse(impulseX * direction, impulseY);
    }
}