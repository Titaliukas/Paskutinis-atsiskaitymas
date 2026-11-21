package lt.ktu.paskutinisatsiskaitymas.domain;

/** Didina stresą; jei reikšmė nepasikeitė (per maža žala), grandinė sustoja. */
final class StressHandler extends DamageHandler {
    @Override
    protected boolean process(DamageContext context) {
        return !context.target().addStress(context.amount());
    }
}