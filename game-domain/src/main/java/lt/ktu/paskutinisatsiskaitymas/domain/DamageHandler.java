package lt.ktu.paskutinisatsiskaitymas.domain;

/** Handler: apdoroja žalą ir arba sustabdo grandinę, arba perduoda ją kitam elementui. */
abstract class DamageHandler {
    private DamageHandler next;

    /** Grąžina kitą elementą, kad grandinę būtų galima surašyti iš eilės. */
    final DamageHandler linkWith(DamageHandler next) {
        this.next = java.util.Objects.requireNonNull(next, "next");
        return next;
    }

    final void handle(DamageContext context) {
        if (!process(context) && next != null) {
            next.handle(context);
        }
    }

    /** True, jei užklausa baigta ir grandinė turi sustoti. */
    protected abstract boolean process(DamageContext context);
}