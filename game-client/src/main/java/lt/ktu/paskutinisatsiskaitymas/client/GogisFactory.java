package lt.ktu.paskutinisatsiskaitymas.client;

/** Concrete factory: the Gogis renderer family. */
final class GogisFactory implements CharacterFactory {
    @Override
    public BodyRenderer createBodyRenderer() {
        return new GogisBody();
    }

    @Override
    public ProjectileRenderer createProjectileRenderer() {
        return new GogisProjectile();
    }

    @Override
    public MeleeRenderer createMeleeRenderer() {
        return new GogisMelee();
    }
}
