package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** Gogis throws a rubber duck. The 32x32 art faces right and is mirrored for leftward shots. */
final class GogisProjectile implements ProjectileRenderer {
    /** The sprite is drawn larger than the small hitbox so the pixel art stays readable. */
    private static final double SPRITE_SCALE = 2.0;
    private static final Color FILL = new Color(170, 90, 230);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight) {
        Image image = AssetManager.getInstance().getImage("GOGIS_PROJECTILE");
        if (image != null) {
            double size = Math.max(bounds.getWidth(), bounds.getHeight()) * SPRITE_SCALE;
            SpriteDrawing.drawCentered(g, image, bounds, size, facesRight);
            return;
        }
        Path2D diamond = new Path2D.Double();
        diamond.moveTo(bounds.getCenterX(), bounds.getMinY());
        diamond.lineTo(bounds.getMaxX(), bounds.getCenterY());
        diamond.lineTo(bounds.getCenterX(), bounds.getMaxY());
        diamond.lineTo(bounds.getMinX(), bounds.getCenterY());
        diamond.closePath();
        g.setColor(FILL);
        g.fill(diamond);
    }
}
