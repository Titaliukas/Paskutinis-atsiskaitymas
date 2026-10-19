package lt.ktu.paskutinisatsiskaitymas.client;

/** Concrete factory: the Vacijus renderer family. */
final class VacijusFactory implements CharacterFactory {
    @Override
    public BodyRenderer createBodyRenderer() {
        return new VacijusBody();
    }

    @Override
    public ProjectileRenderer createProjectileRenderer() {
        return new VacijusProjectile();
    }

    @Override
    public MeleeRenderer createMeleeRenderer() {
        return new VacijusMelee();
    }
}
