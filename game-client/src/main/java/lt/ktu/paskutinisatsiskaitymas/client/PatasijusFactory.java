package lt.ktu.paskutinisatsiskaitymas.client;

/** Concrete factory: the Patasijus renderer family. */
final class PatasijusFactory implements CharacterFactory {
    @Override
    public BodyRenderer createBodyRenderer() {
        return new PatasijusBody();
    }

    @Override
    public ProjectileRenderer createProjectileRenderer() {
        return new PatasijusProjectile();
    }

    @Override
    public MeleeRenderer createMeleeRenderer() {
        return new PatasijusMelee();
    }
}
