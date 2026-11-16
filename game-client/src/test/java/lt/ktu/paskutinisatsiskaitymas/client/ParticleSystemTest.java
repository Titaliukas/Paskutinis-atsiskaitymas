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
