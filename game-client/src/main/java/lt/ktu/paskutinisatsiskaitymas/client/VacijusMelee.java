package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** Vacijus pushes a blue block. */
final class VacijusMelee implements MeleeRenderer {
    private static final Color AREA = new Color(70, 140, 255, 110);
    private static final Color EDGE = new Color(70, 140, 255);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds) {
        Graphics2D box = (Graphics2D) g.create();
        try {
            RoundRectangle2D shape = new RoundRectangle2D.Double(bounds.getX(), bounds.getY(),
                    bounds.getWidth(), bounds.getHeight(), 10, 10);
            box.setColor(AREA);
            box.fill(shape);
            box.setColor(EDGE);
            box.setStroke(new BasicStroke(2f));
            box.draw(shape);
        } finally {
            box.dispose();
        }
    }
}
