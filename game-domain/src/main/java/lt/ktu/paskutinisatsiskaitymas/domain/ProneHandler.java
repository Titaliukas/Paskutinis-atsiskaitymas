package lt.ktu.paskutinisatsiskaitymas.domain;

/** Gulintis žaidėjas gauna perpus mažiau žalos; grandinės nestabdo. */
final class ProneHandler extends DamageHandler {
    static final double PRONE_FACTOR = 0.3;

    @Override
    protected boolean process(DamageContext context) {
        if (context.target().isProne()) {
            context.scale(PRONE_FACTOR);
        }
        return false;
    }
}