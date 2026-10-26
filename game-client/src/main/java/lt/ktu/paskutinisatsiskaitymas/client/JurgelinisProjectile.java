package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * Jurgelinis throws a UML class. The art is never mirrored, so the text stays readable in both
 * directions.
 */
final class JurgelinisProjectile implements ProjectileRenderer {
    /** The sprite is drawn larger than the small hitbox so the pixel art stays readable. */
    private static final double SPRITE_SCALE = 2.0;
    private static final Color HALO = new Color(90, 200, 110, 90);
    private static final Color FILL = new Color(90, 200, 110);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight) {
        Image image = AssetManager.getInstance().getImage("JURGELINIS_PROJECTILE");
        if (image != null) {
            double size = Math.max(bounds.getWidth(), bounds.getHeight()) * SPRITE_SCALE;
            SpriteDrawing.drawCentered(g, image, bounds, size, true);
            return;
        }
        double grow = bounds.getWidth() / 3;
        g.setColor(HALO);
        g.fill(new Ellipse2D.Double(bounds.getX() - grow, bounds.getY() - grow,
                bounds.getWidth() + 2 * grow, bounds.getHeight() + 2 * grow));
        g.setColor(FILL);
        g.fill(new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
    }
}
