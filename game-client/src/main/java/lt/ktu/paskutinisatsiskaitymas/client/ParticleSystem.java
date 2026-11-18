package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Owns and advances live particles. Single-threaded: all calls happen on the EDT. */
final class ParticleSystem {
    static final int DEFAULT_MAX_PARTICLES = 3000;

    private final List<Particle> particles = new ArrayList<>();
    private final Random random;
    private final int maxParticles;
    private final ParticleStats stats;

    ParticleSystem() {
        this(new Random(), DEFAULT_MAX_PARTICLES, null);
    }

    ParticleSystem(Random random, int maxParticles) {
        this(random, maxParticles, null);
    }

    /** {@code stats} may be null; otherwise emit, update and draw report their cost to it while it is running. */
    ParticleSystem(Random random, int maxParticles, ParticleStats stats) {
        if (maxParticles < 1) {
            throw new IllegalArgumentException("maxParticles must be positive");
        }
        this.random = random;
        this.maxParticles = maxParticles;
        this.stats = stats;
    }

    /** Emits {@code count} particles; {@code directionX} (-1 or 1) orients directional kinds. */
    void emitBurst(ParticleKind kind, double x, double y, int count, int directionX) {
        emitBurst(kind, x, y, count, directionX, 0.0);
    }

    /** As {@link #emitBurst(ParticleKind, double, double, int, int)}, scattering each spawn point by up to {@code jitter}. */
    void emitBurst(ParticleKind kind, double x, double y, int count, int directionX, double jitter) {
        emitAngledBurst(kind, x, y, count, directionX >= 0 ? 0 : Math.PI, jitter);
    }

    /** Vector-oriented emission, using the same flyweights, cap and accounting as ordinary bursts. */
    void emitAngledBurst(ParticleKind kind, double x, double y, int count, double directionAngle, double jitter) {
        boolean measure = stats != null && stats.running();
        long startBytes = measure ? ParticleStats.allocatedBytes() : 0;
        long startNanos = measure ? System.nanoTime() : 0;
        ParticleType type = ParticleTypeFactory.get(kind);
        int created = Math.min(count, maxParticles);
        int evicted = count - created;
        int overflow = particles.size() + created - maxParticles;
        if (overflow > 0) {
            particles.subList(0, overflow).clear();
            evicted += overflow;
        }
        for (int i = 0; i < created; i++) {
            double spawnX = x + (random.nextDouble() * 2 - 1) * jitter;
            double spawnY = y + (random.nextDouble() * 2 - 1) * jitter;
            double angle = type.directional()
                    ? directionAngle + (random.nextDouble() - 0.5)
                    : random.nextDouble() * Math.PI * 2;
            double speed = type.speed() * (0.5 + random.nextDouble() * 0.5);
            particles.add(new Particle(type, spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed));
        }
        if (measure) {
            long nanos = System.nanoTime() - startNanos;
            stats.recordEmit(kind, created, nanos, ParticleStats.allocatedBytes() - startBytes, evicted,
                    particles.size());
        }
    }

    void update(double seconds) {
        boolean measure = stats != null && stats.running();
        long startBytes = measure ? ParticleStats.allocatedBytes() : 0;
        long startNanos = measure ? System.nanoTime() : 0;
        int before = particles.size();
        for (Particle particle : particles) {
            particle.update(seconds);
        }
        particles.removeIf(particle -> !particle.alive());
        if (measure) {
            long nanos = System.nanoTime() - startNanos;
            stats.recordUpdate(nanos, ParticleStats.allocatedBytes() - startBytes, before - particles.size(),
                    particles.size());
        }
    }

    void draw(Graphics2D g) { draw(g, null); }

    /** A scoped clip for ability particles keeps off-arena entry effects out of letterbox margins. */
    void draw(Graphics2D g, Shape examinatorClip) {
        boolean measure = stats != null && stats.running();
        long startBytes = measure ? ParticleStats.allocatedBytes() : 0;
        long startNanos = measure ? System.nanoTime() : 0;
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        Graphics2D ability = examinatorClip == null ? g : (Graphics2D) g.create();
        try {
            if (examinatorClip != null) {
                ability.clip(examinatorClip);
            }
            for (Particle particle : particles) {
                particle.draw(particle.kind().isExaminator() ? ability : g);
            }
        } finally {
            if (ability != g) {
                ability.dispose();
            }
        }
        if (previous != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
        }
        if (measure) {
            long nanos = System.nanoTime() - startNanos;
            stats.recordDraw(nanos, ParticleStats.allocatedBytes() - startBytes);
        }
    }

    boolean isActive() {
        return !particles.isEmpty();
    }

    int size() {
        return particles.size();
    }

    int count(ParticleKind kind) {
        int total = 0;
        for (Particle particle : particles) {
            if (particle.kind() == kind) {
                total++;
            }
        }
        return total;
    }

    void clear() {
        particles.clear();
    }
}
