package lt.ktu.paskutinisatsiskaitymas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ParticleStressScenarioTest {
    @Test
    void spawnersEmitTheirConfiguredRateAcrossDifferentKinds() {
        ParticleStats stats = new ParticleStats("unit");
        ParticleSystem system = new ParticleSystem(new Random(1), 100_000, stats);
        ParticleStressScenario scenario = new ParticleStressScenario(system, 2, 100, 960, 540);
        stats.start();
        for (int tick = 0; tick < 100; tick++) {
            scenario.tick(0.01);
        }
        ParticleStatsReport report = stats.finish(Map.of());
        long spawned = Long.parseLong(report.value("spawned_total"));
        assertTrue(spawned >= 199 && spawned <= 200, "spawned " + spawned);
        assertTrue(Long.parseLong(report.value("spawned_" + ParticleKind.values()[0].name())) > 0);
        assertTrue(Long.parseLong(report.value("spawned_" + ParticleKind.values()[1].name())) > 0);
        assertEquals("0", report.value("spawned_" + ParticleKind.values()[2].name()));
    }

    @Test
    void spawnersAreLaidOutInsideTheArenaAndCycleKinds() {
        ParticleSystem system = new ParticleSystem(new Random(1), 10, null);
        ParticleStressScenario scenario = new ParticleStressScenario(system, 40, 10, 960, 540);
        assertEquals(40, scenario.spawners().size());
        for (ParticleSpawner spawner : scenario.spawners()) {
            assertTrue(spawner.x() > 0 && spawner.x() < 960);
            assertTrue(spawner.y() > 0 && spawner.y() < 540);
        }
        assertEquals(ParticleKind.values()[0], scenario.spawners().get(0).kind());
        assertEquals(ParticleKind.values()[1], scenario.spawners().get(1).kind());
    }

    @Test
    void rejectsInvalidScenarioAndArguments() {
        ParticleSystem system = new ParticleSystem(new Random(1), 10, null);
        assertThrows(IllegalArgumentException.class, () -> new ParticleStressScenario(system, 0, 10, 960, 540));
        assertThrows(IllegalArgumentException.class,
                () -> new ParticleSpawner(ParticleKind.DUST, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ParticleStressBenchmark.parse(new String[] {"--bogus=1"}));
        assertThrows(IllegalArgumentException.class, () -> ParticleStressBenchmark.parse(new String[] {"--rate=abc"}));
        assertThrows(IllegalArgumentException.class, () -> ParticleStressBenchmark.parse(new String[] {"--cap=0"}));
        ParticleStressBenchmark.Config defaults = ParticleStressBenchmark.parse(new String[0]);
        assertEquals(40, defaults.spawners());
        assertEquals(15.0, ParticleStressBenchmark.parse(new String[] {"--duration=15"}).durationSeconds());
    }
}
