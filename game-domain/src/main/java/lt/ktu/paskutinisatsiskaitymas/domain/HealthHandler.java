package lt.ktu.paskutinisatsiskaitymas.domain;

/** Mažina gyvybes ir pažymi, kad žala pasiekė taikinį. */
final class HealthHandler extends DamageHandler {
    @Override
    protected boolean process(DamageContext context) {
        context.target().loseHealth((int) Math.ceil(context.amount()));
        context.markApplied();
        return false;
    }
}