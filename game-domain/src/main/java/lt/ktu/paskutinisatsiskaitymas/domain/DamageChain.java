package lt.ktu.paskutinisatsiskaitymas.domain;

final class DamageChain {
    private DamageChain() { }

    static DamageHandler standard() {
        DamageHandler head = new ShieldHandler();
        head.linkWith(new ProneHandler())
            .linkWith(new StressHandler())
            .linkWith(new HealthHandler())
            .linkWith(new RespawnHandler());
        return head;
    }

    /** True tik jei žala realiai pasiekė taikinį (ne blokuota skydo, ne per maža). */
    static boolean apply(DamageHandler head, GameCharacter target, double amount) {
        DamageContext context = new DamageContext(target, amount);
        head.handle(context);
        return context.applied();
    }
}