package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/** Patasijus throws an orange ball. */
final class PatasijusProjectile implements ProjectileRenderer {
    private static final Color FILL = new Color(255, 150, 40);
    private static final Color OUTLINE = new Color(150, 70, 10);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds) {
        Ellipse2D ball = new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        g.setColor(FILL);
        g.fill(ball);
        g.setColor(OUTLINE);
        g.draw(ball);
    }
}
