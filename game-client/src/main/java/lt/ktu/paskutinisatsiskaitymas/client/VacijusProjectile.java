package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Vacijus sends a blue data packet. */
final class VacijusProjectile implements ProjectileRenderer {
    private static final Color FILL = new Color(70, 140, 255);
    private static final Color CORE = new Color(200, 225, 255);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds) {
        g.setColor(FILL);
        g.fill(bounds);
        double inset = bounds.getWidth() / 4;
        g.setColor(CORE);
        g.fill(new Rectangle2D.Double(bounds.getX() + inset, bounds.getY() + inset,
                bounds.getWidth() - 2 * inset, bounds.getHeight() - 2 * inset));
    }
}
