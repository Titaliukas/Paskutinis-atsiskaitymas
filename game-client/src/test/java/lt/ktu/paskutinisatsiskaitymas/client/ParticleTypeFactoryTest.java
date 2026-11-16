package lt.ktu.paskutinisatsiskaitymas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParticleTypeFactoryTest {
    @BeforeEach
    void coldCache() {
        ParticleTypeFactory.clear();
    }

    @Test
    void returnsTheSameSharedInstanceForAKindAndDistinctOnesAcrossKinds() {
        ParticleType first = ParticleTypeFactory.get(ParticleKind.HIT);
        assertSame(first, ParticleTypeFactory.get(ParticleKind.HIT));
        assertNotSame(first, ParticleTypeFactory.get(ParticleKind.DUST));
        assertEquals(ParticleKind.HIT, first.kind());
        assertEquals(2, ParticleTypeFactory.cachedTypes());
    }

    @Test
    void thousandsOfParticlesOfOneKindShareOneTypeAndOneSprite() {
        long spritesBefore = ParticleAllocations.spritesCreated();
        ParticleSystem system = new ParticleSystem(new Random(1), 10_000);
        system.emitBurst(ParticleKind.SPARKLE, 0, 0, 5_000, 1);
        assertEquals(5_000, system.size());
        assertEquals(1, ParticleTypeFactory.cachedTypes());
        assertEquals(1, ParticleAllocations.spritesCreated() - spritesBefore);
    }

    @Test
    void everyParticleOfAKindReferencesTheFlyweight() {
        ParticleType type = ParticleTypeFactory.get(ParticleKind.MUZZLE);
        Particle first = new Particle(type, 10, 10, 1, 1);
        Particle second = new Particle(type, 50, 50, -1, 0);
        assertSame(first.type(), second.type());
        assertEquals(ParticleKind.MUZZLE, first.kind());
    }

    @Test
    void extrinsicStateDrivesMotionAndLifetimeWhileTheTypeSuppliesTheConstants() {
        ParticleType type = ParticleTypeFactory.get(ParticleKind.HIT);
        Particle falling = new Particle(type, 0, 0, 0, 0);
        falling.update(0.1);
        assertTrue(falling.alive());
        falling.update(type.lifetime());
        assertTrue(!falling.alive());
        Particle fresh = new Particle(type, 0, 0, 0, 0);
        assertTrue(fresh.alive(), "particles of one type keep independent age");
    }

    @Test
    void drawingUsesTheSharedSpriteAndFadesWithAge() {
        ParticleType type = ParticleTypeFactory.get(ParticleKind.POWERUP_SHIELD);
        assertNotEquals(0, type.sprite().getWidth());
        int fresh = paintedPixels(type, 0.0);
        int nearlyDead = paintedPixels(type, type.lifetime() * 0.95);
        int expired = paintedPixels(type, type.lifetime());
        assertTrue(fresh > 0, "a fresh particle is visible");
        assertTrue(nearlyDead > 0, "a nearly expired particle is still drawn, faintly");
        assertEquals(0, expired, "an expired particle draws nothing");
    }

    private static int paintedPixels(ParticleType type, double age) {
        BufferedImage canvas = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            type.draw(g, 30, 30, age);
        } finally {
            g.dispose();
        }
        int painted = 0;
        for (int y = 0; y < 60; y++) {
            for (int x = 0; x < 60; x++) {
                if ((canvas.getRGB(x, y) >>> 24) != 0) {
                    painted++;
                }
            }
        }
        return painted;
    }
}
