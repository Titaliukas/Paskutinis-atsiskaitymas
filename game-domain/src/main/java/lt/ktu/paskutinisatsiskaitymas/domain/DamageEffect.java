package lt.ktu.paskutinisatsiskaitymas.domain;

/** Concrete implementor: reduces health of players and NPCs. */
public record DamageEffect(int amount) implements AttackEffect {
    public DamageEffect {
        if (amount <= 0) {
            throw new IllegalArgumentException("Damage must be positive");
        }
    }

    @Override
    public void applyTo(GameCharacter target, int direction) {
        target.takeDamage(amount);
    }

    @Override
    public void applyTo(NPC target, int direction) {
        target.takeDamage(amount);
    }
}