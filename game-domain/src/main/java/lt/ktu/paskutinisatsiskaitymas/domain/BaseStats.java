package lt.ktu.paskutinisatsiskaitymas.domain;

/** Concrete component: an unmodified character. Stateless, so one shared instance ends every chain. */
public final class BaseStats implements CharacterStats {
    public static final BaseStats INSTANCE = new BaseStats();

    private BaseStats() { }

    @Override
    public double speedMultiplier() {
        return 1.0;
    }

    @Override
    public double jumpMultiplier() {
        return 1.0;
    }

    @Override
    public boolean shielded() {
        return false;
    }

    @Override
    public CharacterStats tick(double seconds) {
        return this;
    }

    @Override
    public CharacterStats without(Class<? extends StatsDecorator> type) {
        return this;
    }

    @Override
    public String describe() {
        return "Base";
    }
}
