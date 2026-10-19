package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/** Patasijus throws a cat. The 32x32 art faces right and is mirrored for leftward shots. */
final class PatasijusProjectile implements ProjectileRenderer {
    /** The sprite is drawn larger than the small hitbox so the pixel art stays readable. */
    private static final double SPRITE_SCALE = 2.0;
    private static final Color FILL = new Color(255, 150, 40);
    private static final Color OUTLINE = new Color(150, 70, 10);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight) {
        Image image = AssetManager.getInstance().getImage("PATASIJUS_PROJECTILE");
        if (image != null) {
            double size = Math.max(bounds.getWidth(), bounds.getHeight()) * SPRITE_SCALE;
            SpriteDrawing.drawCentered(g, image, bounds, size, facesRight);
            return;
        }
        Ellipse2D ball = new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        g.setColor(FILL);
        g.fill(ball);
        g.setColor(OUTLINE);
        g.draw(ball);
    }
}
