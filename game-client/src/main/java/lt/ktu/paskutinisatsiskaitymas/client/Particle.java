package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;

/**
 * One short-lived particle. It keeps only its extrinsic state (position, velocity, age) and a reference to the shared
 * {@link ParticleType} flyweight that supplies everything else.
 */
final class Particle {
    private final ParticleType type;
    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private double age;

    Particle(ParticleType type, double x, double y, double velocityX, double velocityY) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }

    void update(double seconds) {
        velocityY += type.gravity() * seconds;
        double damping = Math.max(0.0, 1.0 - type.drag() * seconds);
        velocityX *= damping;
        velocityY *= damping;
        x += velocityX * seconds;
        y += velocityY * seconds;
        age += seconds;
    }

    boolean alive() {
        return age < type.lifetime();
    }

    ParticleType type() {
        return type;
    }

    ParticleKind kind() {
        return type.kind();
    }

    void draw(Graphics2D g) {
        type.draw(g, x, y, age);
    }
}
