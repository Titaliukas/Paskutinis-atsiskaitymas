package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.Objects;

/** One character's renderer family, created together from a single factory so the parts always match. */
record CharacterSkin(BodyRenderer body, ProjectileRenderer projectile, MeleeRenderer melee) {
    CharacterSkin {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(projectile, "projectile");
        Objects.requireNonNull(melee, "melee");
    }

    static CharacterSkin from(CharacterFactory factory) {
        return new CharacterSkin(factory.createBodyRenderer(), factory.createProjectileRenderer(),
                factory.createMeleeRenderer());
    }
}
