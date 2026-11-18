package lt.ktu.paskutinisatsiskaitymas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ParticleSystemTest {
    @Test
    void particlesExpireAfterTheirLifetime() {
        ParticleSystem system = new ParticleSystem(new Random(1), 100);
        system.emitBurst(ParticleKind.MUZZLE, 10, 10, 5, 1);
        assertEquals(5, system.size());
        assertTrue(system.isActive());

        system.update(ParticleKind.MUZZLE.lifetimeSeconds() / 2);
        assertEquals(5, system.size());
        system.update(ParticleKind.MUZZLE.lifetimeSeconds());
        assertEquals(0, system.size());
        assertFalse(system.isActive());
    }

    @Test
    void capDropsTheOldestParticles() {
        ParticleSystem system = new ParticleSystem(new Random(1), 10);
        system.emitBurst(ParticleKind.HIT, 0, 0, 10, 1);
        system.emitBurst(ParticleKind.DUST, 0, 0, 4, 1);
        assertEquals(10, system.size());
        assertEquals(4, system.count(ParticleKind.DUST));
        assertEquals(6, system.count(ParticleKind.HIT));
    }

    @Test
    void angledBurstsKeepCapFlyweightsAndOrientVerticalSparks() {
        ParticleSystem system = new ParticleSystem(new Random(1), 10);
        system.emitAngledBurst(ParticleKind.EXAMINATOR_SPARK, 100, 100, 20, -Math.PI / 2, 0);
        assertEquals(10, system.size());
        assertEquals(10, system.count(ParticleKind.EXAMINATOR_SPARK));
        assertEquals(ParticleTypeFactory.get(ParticleKind.EXAMINATOR_SPARK),
                ParticleTypeFactory.get(ParticleKind.EXAMINATOR_SPARK));
        system.update(0.1);
        var image = new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { system.draw(graphics); } finally { graphics.dispose(); }
        boolean above = false;
        for (int y = 0; y < 98; y++) {
            for (int x = 0; x < 200; x++) { above |= image.getRGB(x, y) >>> 24 != 0; }
        }
        assertTrue(above, "Sparks must travel upward for a vertical aim vector");
        system.update(1);
        assertFalse(system.isActive());
    }

    @Test
    void clearRemovesEverything() {
        ParticleSystem system = new ParticleSystem(new Random(1), 10);
        system.emitBurst(ParticleKind.IMPACT, 0, 0, 3, 1);
        system.clear();
        assertEquals(0, system.size());
    }

    @Test
    void rejectsNonPositiveCap() {
        assertThrows(IllegalArgumentException.class, () -> new ParticleSystem(new Random(1), 0));
    }
}
