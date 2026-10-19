package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Sprite of Jurgelinis. */
final class JurgelinisBody implements BodyRenderer {
    private static final Color FALLBACK = new Color(90, 200, 110);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight) {
        SpriteDrawing.drawOrFill(g, "JURGELINIS", bounds, facesRight, FALLBACK);
    }
}
