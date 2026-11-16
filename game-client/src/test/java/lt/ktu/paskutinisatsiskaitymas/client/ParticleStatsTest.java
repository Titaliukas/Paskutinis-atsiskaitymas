package lt.ktu.paskutinisatsiskaitymas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParticleStatsTest {
    @Test
    void timingColumnsUseNearestRankPercentiles() {
        ParticleStats stats = new ParticleStats("unit");
        stats.start();
        for (int millis = 1; millis <= 100; millis++) {
            stats.recordUpdate(millis * 1_000_000L, 0, 0, 0);
        }
        ParticleStatsReport report = stats.finish(Map.of());
        assertEquals("50.500", report.value("update_ms_avg"));
        assertEquals("50.000", report.value("update_ms_p50"));
        assertEquals("95.000", report.value("update_ms_p95"));
        assertEquals("99.000", report.value("update_ms_p99"));
        assertEquals("100.000", report.value("update_ms_max"));
        assertEquals("0.000", report.value("draw_ms_avg"));
    }

    @Test
    void particleSystemReportsSpawnedExpiredEvictedAndPeakLive() {
        ParticleStats stats = new ParticleStats("unit");
        ParticleSystem system = new ParticleSystem(new Random(1), 6, stats);
        stats.start();
        system.emitBurst(ParticleKind.HIT, 0, 0, 5, 1);
        system.emitBurst(ParticleKind.DUST, 0, 0, 3, 1);
        system.update(10.0);
        ParticleStatsReport report = stats.finish(Map.of(ParticleStats.PARTICLE_CAP, "6"));
        assertEquals("8", report.value("spawned_total"));
        assertEquals("5", report.value("spawned_HIT"));
        assertEquals("3", report.value("spawned_DUST"));
        assertEquals("0", report.value("spawned_MUZZLE"));
        assertEquals("2", report.value("evicted_by_cap"));
        assertEquals("6", report.value("expired"));
        assertEquals("6", report.value("peak_live"));
        assertEquals("6", report.value("particle_cap"));
        assertTrue(Long.parseLong(report.value("sprites_created")) > 0);
        assertEquals("naive", report.value("implementation"));
        assertEquals("unit", report.value("scenario"));
    }

    @Test
    void nothingIsRecordedBeforeStartOrAfterStop() {
        ParticleStats stats = new ParticleStats("unit");
        ParticleSystem system = new ParticleSystem(new Random(1), 100, stats);
        system.emitBurst(ParticleKind.HIT, 0, 0, 5, 1);
        stats.start();
        system.emitBurst(ParticleKind.HIT, 0, 0, 2, 1);
        stats.stop();
        system.emitBurst(ParticleKind.HIT, 0, 0, 7, 1);
        assertEquals("2", stats.report(Map.of()).value("spawned_total"));
    }

    @Test
    void csvHeaderIsWrittenOnceAndRowsAreAppended(@TempDir Path directory) throws IOException {
        Path csv = directory.resolve("stats.csv");
        ParticleStats stats = new ParticleStats("unit");
        stats.start();
        ParticleStatsReport report = stats.finish(Map.of());
        ParticleStatsSink.appendCsv(csv, report);
        ParticleStatsSink.appendCsv(csv, report);
        List<String> lines = Files.readAllLines(csv);
        assertEquals(3, lines.size());
        assertEquals(report.csvHeader(), lines.get(0));
        assertEquals(report.toCsvRow(), lines.get(1));
        assertEquals(report.columns().size(), lines.get(0).split(",", -1).length);
    }

    @Test
    void csvQuotesValuesThatContainCommasOrQuotes() {
        ParticleStatsReport report = new ParticleStatsReport(List.of(
                Map.entry("a", "plain"), Map.entry("b", "x,y"), Map.entry("c", "say \"hi\"")));
        assertEquals("a,b,c", report.csvHeader());
        assertEquals("plain,\"x,y\",\"say \"\"hi\"\"\"", report.toCsvRow());
        assertTrue(report.toConsoleTable().contains("plain"));
    }
}
