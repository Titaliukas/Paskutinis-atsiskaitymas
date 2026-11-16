package lt.ktu.paskutinisatsiskaitymas.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Manual benchmark, off by default. Measures heap per live particle and steady-state frame time (update + draw +
 * respawn churn). Run: {@code ./mvnw -B -ntp -pl game-client -am test -Dtest=ParticleBenchmarkTest
 * -Dparticles.benchmark=true -Dsurefire.failIfNoSpecifiedTests=false}. Results go to target/particle-benchmark.txt.
 */
@EnabledIfSystemProperty(named = "particles.benchmark", matches = "true")
class ParticleBenchmarkTest {
    private static final int[] COUNTS = {1_000, 10_000, 50_000};
    private static final int WARMUP_FRAMES = 20;
    private static final int MEASURED_FRAMES = 100;
    private static final double FRAME_SECONDS = 1.0 / 60.0;

    @Test
    void measureMemoryAndFrameTime() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("%-10s %-16s %-14s", "particles", "bytes/particle", "median ms/frame"));
        for (int count : COUNTS) {
            double bytes = bytesPerParticle(count);
            double millis = medianFrameMillis(count);
            lines.add(String.format("%-10d %-16.1f %-14.3f", count, bytes, millis));
        }
        lines.forEach(System.out::println);
        Path target = Path.of("target");
        Files.createDirectories(target);
        Files.write(target.resolve("particle-benchmark.txt"), lines);
        assertTrue(lines.size() > 1);
    }

    private static double bytesPerParticle(int count) {
        long before = usedHeap();
        ParticleSystem system = new ParticleSystem(new Random(7), count + 1);
        fill(system, count, new Random(7));
        long after = usedHeap();
        double result = (after - before) / (double) system.size();
        system.clear();
        return result;
    }

    private static double medianFrameMillis(int count) {
        ParticleSystem system = new ParticleSystem(new Random(7), count + 1);
        Random random = new Random(7);
        BufferedImage canvas = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        double[] samples = new double[MEASURED_FRAMES];
        try {
            for (int frame = 0; frame < WARMUP_FRAMES + MEASURED_FRAMES; frame++) {
                long start = System.nanoTime();
                fill(system, count, random);
                system.update(FRAME_SECONDS);
                system.draw(g);
                long elapsed = System.nanoTime() - start;
                if (frame >= WARMUP_FRAMES) {
                    samples[frame - WARMUP_FRAMES] = elapsed / 1e6;
                }
            }
        } finally {
            g.dispose();
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    /** Tops the system back up to {@code target} live particles, spread over a 960x540 arena in bursts of ten. */
    private static void fill(ParticleSystem system, int target, Random random) {
        ParticleKind[] kinds = ParticleKind.values();
        while (system.size() < target) {
            int burst = Math.min(10, target - system.size());
            system.emitBurst(kinds[random.nextInt(kinds.length)], random.nextDouble() * 960,
                    random.nextDouble() * 540, burst, random.nextBoolean() ? 1 : -1);
        }
    }

    private static long usedHeap() {
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
