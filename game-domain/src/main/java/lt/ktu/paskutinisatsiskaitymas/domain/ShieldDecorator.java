package lt.ktu.paskutinisatsiskaitymas.domain;

/** Concrete decorator: blocks incoming damage while a shield item is active. */
public final class ShieldDecorator extends StatsDecorator {
    public ShieldDecorator(CharacterStats inner, double seconds) {
        super(inner, seconds);
    }

    @Override
    public boolean shielded() {
        return true;
    }

    @Override
    protected String name() {
        return "Shield";
    }
}
