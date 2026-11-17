package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Flyweight: the immutable state that every particle of one {@link ParticleKind} shares (appearance, physics
 * constants, the pre-rendered sprite and the fade composites). One instance per kind lives in
 * {@link ParticleTypeFactory}; each {@link Particle} keeps only its own position, velocity and age (the extrinsic
 * state) and passes it to {@link #draw}.
 */
final class ParticleType {
    /** World-space size of one "pixel": positions and sizes snap to this grid for a chunky retro look. */
    private static final int PIXEL = 3;
    private static final int SPRITE_CELLS = 4;
    private static final int ALPHA_STEPS = 4;

    private final ParticleKind kind;
    private final double lifetime;
    private final double gravity;
    private final double drag;
    private final double speed;
    private final boolean directional;
    private final int diameter;
    private final BufferedImage sprite;
    private final AlphaComposite[] fades = new AlphaComposite[ALPHA_STEPS + 1];

    ParticleType(ParticleKind kind) {
        this.kind = kind;
        this.lifetime = kind.lifetimeSeconds();
        this.gravity = kind.gravity();
        this.drag = kind.drag();
        this.speed = kind.speed();
        this.directional = kind.directional();
        this.diameter = Math.max(PIXEL, snap(kind.size() * 2));
        this.sprite = createSprite(kind.color(), diameter);
        for (int step = 0; step <= ALPHA_STEPS; step++) {
            fades[step] = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, step / (float) ALPHA_STEPS);
        }
    }

    /**
     * A 4x4 grid of hard-edged blocks (bright core, darker rim, clipped corners), rendered once at the exact on-screen
     * size so drawing never has to scale it.
     */
    private static BufferedImage createSprite(Color color, int pixels) {
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
                    int left = column * pixels / SPRITE_CELLS;
                    int top = row * pixels / SPRITE_CELLS;
                    g.fillRect(left, top, (column + 1) * pixels / SPRITE_CELLS - left,
                            (row + 1) * pixels / SPRITE_CELLS - top);
                }
            }
        } finally {
            g.dispose();
        }
        ParticleAllocations.recordSprite((long) pixels * pixels * Integer.BYTES);
        return image;
    }

    ParticleKind kind() {
        return kind;
    }

    double lifetime() {
        return lifetime;
    }

    double gravity() {
        return gravity;
    }

    double drag() {
        return drag;
    }

    double speed() {
        return speed;
    }

    boolean directional() {
        return directional;
    }

    BufferedImage sprite() {
        return sprite;
    }

    /**
     * Draws one particle of this type given its extrinsic state, fading out in a few coarse steps. Callers set
     * nearest-neighbour interpolation.
     */
    void draw(Graphics2D g, double x, double y, double age) {
        double remaining = Math.max(0.0, Math.min(1.0, 1.0 - age / lifetime));
        int step = (int) Math.ceil(remaining * ALPHA_STEPS);
        if (step == 0) {
            return;
        }
        Composite previous = g.getComposite();
        g.setComposite(fades[step]);
        g.drawImage(sprite, snap(x) - diameter / 2, snap(y) - diameter / 2, null);
        g.setComposite(previous);
    }

    private static int snap(double value) {
        return (int) Math.round(value / PIXEL) * PIXEL;
    }
}
