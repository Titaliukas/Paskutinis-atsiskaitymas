package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterFactoryTest {
    private static final Map<String, List<Class<?>>> FAMILIES = Map.of(
            "PATASIJUS", List.of(PatasijusFactory.class, PatasijusBody.class, PatasijusProjectile.class,
                    PatasijusMelee.class),
            "VACIJUS", List.of(VacijusFactory.class, VacijusBody.class, VacijusProjectile.class,
                    VacijusMelee.class),
            "JURGELINIS", List.of(JurgelinisFactory.class, JurgelinisBody.class, JurgelinisProjectile.class,
                    JurgelinisMelee.class),
            "GOGIS", List.of(GogisFactory.class, GogisBody.class, GogisProjectile.class, GogisMelee.class));

    @Test
    void everySelectableCharacterHasAFactory() {
        assertEquals(Set.copyOf(Hello.CHARACTERS), FAMILIES.keySet());
        for (String character : Hello.CHARACTERS) {
            assertNotNull(CharacterFactories.forCharacter(character), character);
        }
    }

    @Test
    void eachFactoryCreatesOnlyItsOwnFamily() {
        FAMILIES.forEach((character, family) -> {
            CharacterFactory factory = CharacterFactories.forCharacter(character);
            assertInstanceOf(family.get(0), factory, character);
            CharacterSkin skin = CharacterSkin.from(factory);
            assertInstanceOf(family.get(1), skin.body(), character);
            assertInstanceOf(family.get(2), skin.projectile(), character);
            assertInstanceOf(family.get(3), skin.melee(), character);
        });
    }

    @Test
    void familiesAreDistinct() {
        Set<Class<?>> projectiles = Hello.CHARACTERS.stream()
                .map(character -> CharacterFactories.forCharacter(character).createProjectileRenderer().getClass())
                .collect(Collectors.toSet());
        assertEquals(Hello.CHARACTERS.size(), projectiles.size());
    }

    @Test
    void unknownCharacterHasNoFactory() {
        assertNull(CharacterFactories.forCharacter(null));
        assertNull(CharacterFactories.forCharacter("WARRIOR"));
    }

    @Test
    void everyRendererDrawsHeadlessly() {
        BufferedImage canvas = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Rectangle2D bounds = new Rectangle2D.Double(40, 40, 42, 64);
        for (String character : Hello.CHARACTERS) {
            CharacterSkin skin = CharacterSkin.from(CharacterFactories.forCharacter(character));
            Graphics2D g = canvas.createGraphics();
            try {
                assertDoesNotThrow(() -> {
                    skin.body().draw(g, bounds, true);
                    skin.body().draw(g, bounds, false);
                    skin.projectile().draw(g, new Rectangle2D.Double(10, 10, 16, 16));
                    skin.melee().draw(g, bounds);
                }, character);
            } finally {
                g.dispose();
            }
        }
    }
}
