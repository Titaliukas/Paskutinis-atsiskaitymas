package lt.ktu.paskutinisatsiskaitymas.domain;

/** Galutinis elementas: jei gyvybių nebeliko, atgaivina žaidėją. */
final class RespawnHandler extends DamageHandler {
    @Override
    protected boolean process(DamageContext context) {
        context.target().respawnIfDead();
        return true;
    }
}