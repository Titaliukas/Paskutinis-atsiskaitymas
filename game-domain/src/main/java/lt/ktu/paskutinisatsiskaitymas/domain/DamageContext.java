package lt.ktu.paskutinisatsiskaitymas.domain;

/** Žalos užklausa; elementai gali keisti sumą ir pažymėti, ar žala pasiekė taikinį. */
final class DamageContext {
    private final GameCharacter target;
    private double amount;
    private boolean applied;

    DamageContext(GameCharacter target, double amount) {
        this.target = java.util.Objects.requireNonNull(target, "target");
        this.amount = NPCGeometry.positive(amount, "damage");
    }

    GameCharacter target() { return target; }
    double amount() { return amount; }

    void scale(double factor) {
        amount = NPCGeometry.positive(amount * NPCGeometry.positive(factor, "factor"), "damage");
    }

    boolean applied() { return applied; }
    void markApplied() { applied = true; }
}