package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** Gogis throws a purple diamond. */
final class GogisProjectile implements ProjectileRenderer {
    private static final Color FILL = new Color(170, 90, 230);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds) {
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
