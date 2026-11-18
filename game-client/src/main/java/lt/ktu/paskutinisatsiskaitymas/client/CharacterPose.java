package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** One posture transform for every renderer family: keep sprite proportions and the feet anchor. */
final class CharacterPose {
    private CharacterPose() { }

    static Rectangle2D bounds(Rectangle2D standing, boolean prone) {
        return prone ? new Rectangle2D.Double(standing.getCenterX() - standing.getHeight() / 2,
                standing.getMaxY() - standing.getWidth(), standing.getHeight(), standing.getWidth()) : standing;
    }

    static void draw(Graphics2D graphics, BodyRenderer renderer, Rectangle2D standing,
            boolean facesRight, boolean prone, Color fallback) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            if (prone) {
                g.translate(standing.getCenterX(), standing.getMaxY() - standing.getWidth() / 2);
                g.rotate(facesRight ? Math.PI / 2 : -Math.PI / 2);
                g.translate(-standing.getCenterX(), -standing.getCenterY());
            }
            if (renderer != null) {
                renderer.draw(g, standing, facesRight);
            } else {
                g.setColor(fallback);
                g.fill(standing);
            }
        } finally {
            g.dispose();
        }
    }
}
