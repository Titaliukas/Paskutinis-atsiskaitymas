package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Heavy particle benchmark: opens a window, floods it with particles from many spawners for a fixed time, then prints
 * the statistics, appends them to the CSV and exits on its own.
 * Usage: {@code ParticleStressBenchmark [--duration=20] [--warmup=3] [--spawners=40] [--rate=1000] [--cap=200000]}
 * (rate is particles per second per spawner).
 */
public final class ParticleStressBenchmark {
    private static final int ARENA_WIDTH = 960;
    private static final int ARENA_HEIGHT = 540;
    private static final String USAGE = "Usage: ParticleStressBenchmark [--duration=seconds] [--warmup=seconds] "
            + "[--spawners=n] [--rate=particlesPerSecondPerSpawner] [--cap=maxLiveParticles]";

    record Config(double durationSeconds, double warmupSeconds, int spawners, double ratePerSpawner, int cap) {
        Config {
            if (durationSeconds <= 0 || warmupSeconds < 0 || spawners < 1 || ratePerSpawner <= 0 || cap < 1) {
                throw new IllegalArgumentException(USAGE);
            }
        }
    }

    private final Config config;
    private final ParticleStatsSink sink;
    private final ParticleStats stats = new ParticleStats("stress");
    private final ParticleSystem system;
    private final ParticleStressScenario scenario;
    private final JFrame frame = new JFrame("Particle stress benchmark");
    private final Timer timer;
    private StressPanel panel;
    private long beginNanos;
    private long lastNanos;
    private long measureStartNanos;
    private boolean measuring;
    private long fpsWindowStart;
    private int fpsWindowFrames;
    private double shownFps;

    ParticleStressBenchmark(Config config, ParticleStatsSink sink) {
        this.config = config;
        this.sink = sink;
        this.system = new ParticleSystem(new Random(42), config.cap(), stats);
        this.scenario = new ParticleStressScenario(system, config.spawners(), config.ratePerSpawner(), ARENA_WIDTH, ARENA_HEIGHT);
        this.timer = new Timer(1, event -> onTick());
    }

    public static void main(String[] args) {
        Config config = parse(args);
        SwingUtilities.invokeLater(() -> new ParticleStressBenchmark(config, ParticleStatsSink.consoleAndCsv()).run());
    }

    static Config parse(String[] args) {
        double duration = 20;
        double warmup = 3;
        int spawners = 40;
        double rate = 1000;
        int cap = 200_000;
        for (String arg : args) {
            int split = arg.indexOf('=');
            if (!arg.startsWith("--") || split < 0) {
                throw new IllegalArgumentException(USAGE);
            }
            String value = arg.substring(split + 1);
            try {
                switch (arg.substring(2, split)) {
                    case "duration" -> duration = Double.parseDouble(value);
                    case "warmup" -> warmup = Double.parseDouble(value);
                    case "spawners" -> spawners = Integer.parseInt(value);
                    case "rate" -> rate = Double.parseDouble(value);
                    case "cap" -> cap = Integer.parseInt(value);
                    default -> throw new IllegalArgumentException(USAGE);
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(USAGE, exception);
            }
        }
        return new Config(duration, warmup, spawners, rate, cap);
    }

    private void run() {
        panel = new StressPanel();
        frame.add(panel);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                timer.stop();
                System.err.println("Benchmark aborted; no statistics were written.");
                System.exit(1);
            }
        });
        frame.setVisible(true);
        System.out.printf(Locale.ROOT, "Stress benchmark: %d spawners x %.0f/s, cap %d, warm-up %.1fs, run %.1fs%n",
                config.spawners(), config.ratePerSpawner(), config.cap(), config.warmupSeconds(),
                config.durationSeconds());
        timer.start();
    }

    private void onTick() {
        long now = System.nanoTime();
        if (beginNanos == 0) {
            beginNanos = now;
            fpsWindowStart = now;
        }
        double seconds = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (!measuring && (now - beginNanos) / 1e9 >= config.warmupSeconds()) {
            measuring = true;
            measureStartNanos = now;
            stats.start();
        }
        long frameStart = System.nanoTime();
        scenario.tick(seconds);
        panel.paintImmediately(0, 0, ARENA_WIDTH, ARENA_HEIGHT);
        stats.recordFrame(System.nanoTime() - frameStart);
        updateFps(now);
        if (measuring && (now - measureStartNanos) / 1e9 >= config.durationSeconds()) {
            finish();
        }
    }

    private void updateFps(long now) {
        fpsWindowFrames++;
        if (now - fpsWindowStart >= 500_000_000L) {
            shownFps = fpsWindowFrames / ((now - fpsWindowStart) / 1e9);
            fpsWindowFrames = 0;
            fpsWindowStart = now;
        }
    }

    private void finish() {
        timer.stop();
        stats.stop();
        long live = system.size();
        long withParticles = ParticleStats.usedHeapAfterGc();
        system.clear();
        long withoutParticles = ParticleStats.usedHeapAfterGc();
        double retained = live == 0 ? 0 : Math.max(0, withParticles - withoutParticles) / (double) live;
        sink.publish(stats.report(Map.of(
                ParticleStats.SPAWNERS, String.valueOf(config.spawners()),
                ParticleStats.RATE_PER_SPAWNER, String.format(Locale.ROOT, "%.1f", config.ratePerSpawner()),
                ParticleStats.PARTICLE_CAP, String.valueOf(config.cap()),
                ParticleStats.RETAINED_BYTES_PER_PARTICLE, String.format(Locale.ROOT, "%.1f", retained))));
        frame.dispose();
        System.exit(0);
    }

    private final class StressPanel extends JPanel {
        private static final Color BACKGROUND = new Color(20, 24, 34);

        StressPanel() {
            setPreferredSize(new Dimension(ARENA_WIDTH, ARENA_HEIGHT));
            setBackground(BACKGROUND);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                system.draw(g);
                g.setColor(Color.WHITE);
                double elapsed = measuring ? (System.nanoTime() - measureStartNanos) / 1e9 : 0;
                String phase = measuring
                        ? String.format(Locale.ROOT, "MEASURING  %.1fs left", Math.max(0, config.durationSeconds() - elapsed))
                        : "WARM-UP (not measured)";
                g.drawString(String.format(Locale.ROOT, "%s | live %,d | %.0f fps | %d spawners",
                        phase, system.size(), shownFps, config.spawners()), 10, 18);
            } finally {
                g.dispose();
            }
        }
    }
}
