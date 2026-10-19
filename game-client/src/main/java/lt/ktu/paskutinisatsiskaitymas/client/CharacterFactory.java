package lt.ktu.paskutinisatsiskaitymas.client;

/** Abstract factory: creates one character's matching body, projectile and melee renderers. */
interface CharacterFactory {
    BodyRenderer createBodyRenderer();

    ProjectileRenderer createProjectileRenderer();

    MeleeRenderer createMeleeRenderer();
}
