package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Owns and advances live particles. Single-threaded: all calls happen on the EDT. */
final class ParticleSystem {
    static final int DEFAULT_MAX_PARTICLES = 3000;

    private final List<Particle> particles = new ArrayList<>();
    private final Random random;
    private final int maxParticles;

    ParticleSystem() {
        this(new Random(), DEFAULT_MAX_PARTICLES);
    }

    ParticleSystem(Random random, int maxParticles) {
        if (maxParticles < 1) {
            throw new IllegalArgumentException("maxParticles must be positive");
        }
        this.random = random;
        this.maxParticles = maxParticles;
    }

    /** Emits {@code count} particles; {@code directionX} (-1 or 1) orients directional kinds. */
    void emitBurst(ParticleKind kind, double x, double y, int count, int directionX) {
        emitBurst(kind, x, y, count, directionX, 0.0);
    }

    /** As {@link #emitBurst(ParticleKind, double, double, int, int)}, scattering each spawn point by up to {@code jitter}. */
    void emitBurst(ParticleKind kind, double x, double y, int count, int directionX, double jitter) {
        for (int i = 0; i < count; i++) {
            double spawnX = x + (random.nextDouble() * 2 - 1) * jitter;
            double spawnY = y + (random.nextDouble() * 2 - 1) * jitter;
            double angle = kind.directional()
                    ? (directionX >= 0 ? 0.0 : Math.PI) + (random.nextDouble() - 0.5)
                    : random.nextDouble() * Math.PI * 2;
            double speed = kind.speed() * (0.5 + random.nextDouble() * 0.5);
            if (particles.size() >= maxParticles) {
                particles.remove(0);
            }
            particles.add(new Particle(kind, spawnX, spawnY, Math.cos(angle) * speed, Math.sin(angle) * speed));
        }
    }

    void update(double seconds) {
        for (Particle particle : particles) {
            particle.update(seconds);
        }
        particles.removeIf(particle -> !particle.alive());
    }

    void draw(Graphics2D g) {
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (Particle particle : particles) {
            particle.draw(g);
        }
        if (previous != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
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
