package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * One short-lived pixel-art particle. Every instance owns all of its data, including its own copy of the appearance
 * (colour, size, lifetime, physics constants and a pre-rendered block sprite) alongside its per-frame motion state.
 */
final class Particle {
    /** World-space size of one "pixel": positions and sizes snap to this grid for a chunky retro look. */
    private static final int PIXEL = 3;
    private static final int SPRITE_CELLS = 4;
    private static final int CELL_PIXELS = 4;
    private static final int ALPHA_STEPS = 4;

    // Appearance and behaviour, copied into every particle.
    private final ParticleKind kind;
    private final Color color;
    private final double size;
    private final double lifetime;
    private final double gravity;
    private final double drag;
    private final BufferedImage sprite;
    // Per-frame state.
    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private double age;

    Particle(ParticleKind kind, double x, double y, double velocityX, double velocityY) {
        this.kind = kind;
        this.color = new Color(kind.color().getRGB(), true);
        this.size = kind.size();
        this.lifetime = kind.lifetimeSeconds();
        this.gravity = kind.gravity();
        this.drag = kind.drag();
        this.sprite = createSprite(color);
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }

    /** A 4x4 grid of hard-edged blocks: bright core, darker rim, clipped corners. */
    private static BufferedImage createSprite(Color color) {
        int pixels = SPRITE_CELLS * CELL_PIXELS;
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        Color rim = new Color((int) (color.getRed() * 0.7), (int) (color.getGreen() * 0.7),
                (int) (color.getBlue() * 0.7));
        Color core = new Color(Math.min(255, color.getRed() + 60), Math.min(255, color.getGreen() + 60),
                Math.min(255, color.getBlue() + 60));
        Graphics2D g = image.createGraphics();
        try {
            for (int row = 0; row < SPRITE_CELLS; row++) {
                for (int column = 0; column < SPRITE_CELLS; column++) {
                    boolean edgeRow = row == 0 || row == SPRITE_CELLS - 1;
                    boolean edgeColumn = column == 0 || column == SPRITE_CELLS - 1;
                    if (edgeRow && edgeColumn) {
                        continue;
                    }
                    g.setColor(edgeRow || edgeColumn ? rim : core);
                    g.fillRect(column * CELL_PIXELS, row * CELL_PIXELS, CELL_PIXELS, CELL_PIXELS);
                }
            }
        } finally {
            g.dispose();
        }
        ParticleAllocations.recordSprite((long) pixels * pixels * Integer.BYTES);
        return image;
    }

    void update(double seconds) {
        velocityY += gravity * seconds;
        double damping = Math.max(0.0, 1.0 - drag * seconds);
        velocityX *= damping;
        velocityY *= damping;
        x += velocityX * seconds;
        y += velocityY * seconds;
        age += seconds;
    }

    boolean alive() {
        return age < lifetime;
    }

    ParticleKind kind() {
        return kind;
    }

    /** Draws in world coordinates, fading out in a few coarse steps. Callers set nearest-neighbour interpolation. */
    void draw(Graphics2D g) {
        double remaining = Math.max(0.0, Math.min(1.0, 1.0 - age / lifetime));
        float alpha = (float) (Math.ceil(remaining * ALPHA_STEPS) / ALPHA_STEPS);
        int diameter = Math.max(PIXEL, snap(size * 2));
        Composite previous = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(sprite, snap(x) - diameter / 2, snap(y) - diameter / 2, diameter, diameter, null);
        g.setComposite(previous);
    }

    private static int snap(double value) {
        return (int) Math.round(value / PIXEL) * PIXEL;
    }
}
