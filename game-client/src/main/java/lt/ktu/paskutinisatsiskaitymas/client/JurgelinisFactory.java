package lt.ktu.paskutinisatsiskaitymas.client;

/** Concrete factory: the Jurgelinis renderer family. */
final class JurgelinisFactory implements CharacterFactory {
    @Override
    public BodyRenderer createBodyRenderer() {
        return new JurgelinisBody();
    }

    @Override
    public ProjectileRenderer createProjectileRenderer() {
        return new JurgelinisProjectile();
    }

    @Override
    public MeleeRenderer createMeleeRenderer() {
        return new JurgelinisMelee();
    }
}
