package lt.ktu.paskutinisatsiskaitymas.domain;

/** Skydas blokuoja visą žalą; užklausa lieka nepritaikyta. */
final class ShieldHandler extends DamageHandler {
    @Override
    protected boolean process(DamageContext context) {
        return context.target().shielded();
    }
}