package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Rectangle2D;

/** Vacijus throws a handheld device. The 32x32 art faces right and is mirrored for leftward shots. */
final class VacijusProjectile implements ProjectileRenderer {
    /** The sprite is drawn larger than the small hitbox so the pixel art stays readable. */
    private static final double SPRITE_SCALE = 2.0;
    private static final Color FILL = new Color(70, 140, 255);
    private static final Color CORE = new Color(200, 225, 255);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight) {
        Image image = AssetManager.getInstance().getImage("VACIJUS_PROJECTILE");
        if (image != null) {
            double size = Math.max(bounds.getWidth(), bounds.getHeight()) * SPRITE_SCALE;
            SpriteDrawing.drawCentered(g, image, bounds, size, facesRight);
            return;
        }
        g.setColor(FILL);
        g.fill(bounds);
        double inset = bounds.getWidth() / 4;
        g.setColor(CORE);
        g.fill(new Rectangle2D.Double(bounds.getX() + inset, bounds.getY() + inset,
                bounds.getWidth() - 2 * inset, bounds.getHeight() - 2 * inset));
    }
}
