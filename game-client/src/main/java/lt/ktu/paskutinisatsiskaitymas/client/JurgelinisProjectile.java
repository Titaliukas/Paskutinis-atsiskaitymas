package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/** Jurgelinis throws a sticky green blob with a halo (his ranged attack slows). */
final class JurgelinisProjectile implements ProjectileRenderer {
    private static final Color HALO = new Color(90, 200, 110, 90);
    private static final Color FILL = new Color(90, 200, 110);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds) {
        double grow = bounds.getWidth() / 3;
        g.setColor(HALO);
        g.fill(new Ellipse2D.Double(bounds.getX() - grow, bounds.getY() - grow,
                bounds.getWidth() + 2 * grow, bounds.getHeight() + 2 * grow));
        g.setColor(FILL);
        g.fill(new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
    }
}
