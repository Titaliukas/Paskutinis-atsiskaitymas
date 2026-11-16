package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;

/** The visual effects the client can emit; each constant carries the appearance and motion of its particles. */
enum ParticleKind {
    MUZZLE(new Color(255, 210, 90), 7.0, 0.30, 0.0, 4.0, 240.0, true),
    HIT(new Color(230, 60, 60), 6.0, 0.55, 500.0, 1.0, 200.0, false),
    IMPACT(new Color(255, 240, 200), 6.0, 0.40, 200.0, 2.0, 120.0, false),
    POWERUP_SHIELD(new Color(120, 200, 255), 6.0, 0.80, -120.0, 1.5, 90.0, false),
    POWERUP_SPEED(new Color(255, 230, 80), 6.0, 0.80, -120.0, 1.5, 90.0, false),
    POWERUP_JUMP(new Color(100, 230, 120), 6.0, 0.80, -120.0, 1.5, 90.0, false),
    DUST(new Color(215, 200, 170), 5.0, 0.45, -30.0, 3.0, 45.0, true),
    /** Heavier yellow streaks left behind a running player who holds a speed boost. */
    SPEED_TRAIL(new Color(255, 230, 80), 8.0, 0.50, -20.0, 3.0, 50.0, true),
    /** Green burst at the feet of a player who holds a jump boost when jumping or landing. */
    JUMP_TRAIL(new Color(100, 230, 120), 8.0, 0.60, 250.0, 1.5, 110.0, false),
    /** Item spawn burst and idle shimmer. */
    SPARKLE(new Color(255, 245, 170), 5.0, 0.70, -40.0, 1.0, 40.0, false),
    /** Grey dust shed by moving platforms. */
    PLATFORM_DUST(new Color(150, 150, 158), 5.0, 0.50, -20.0, 3.0, 40.0, true),
    /** Orbiting motes that surround a shielded player for as long as the shield is up. */
    SHIELD_AURA(new Color(120, 200, 255), 5.0, 0.60, -40.0, 1.0, 10.0, false),
    /** Big green burst when an NPC dies. */
    NPC_DEATH(new Color(110, 220, 150), 8.0, 0.75, 220.0, 1.0, 230.0, false);

    private final Color color;
    private final double size;
    private final double lifetimeSeconds;
    private final double gravity;
    private final double drag;
    private final double speed;
    private final boolean directional;

    ParticleKind(Color color, double size, double lifetimeSeconds, double gravity, double drag, double speed,
            boolean directional) {
        this.color = color;
        this.size = size;
        this.lifetimeSeconds = lifetimeSeconds;
        this.gravity = gravity;
        this.drag = drag;
        this.speed = speed;
        this.directional = directional;
    }

    Color color() {
        return color;
    }

    /** Particle half-width in world units. */
    double size() {
        return size;
    }

    double lifetimeSeconds() {
        return lifetimeSeconds;
    }

    /** Downward acceleration in world units per second squared; negative values rise. */
    double gravity() {
        return gravity;
    }

    double drag() {
        return drag;
    }

    double speed() {
        return speed;
    }

    /** Directional kinds spray along the burst direction; others spread in every direction. */
    boolean directional() {
        return directional;
    }
}
