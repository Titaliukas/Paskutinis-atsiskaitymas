package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Sprite of Vacijus. */
final class VacijusBody implements BodyRenderer {
    private static final Color FALLBACK = new Color(70, 140, 255);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight) {
        SpriteDrawing.drawOrFill(g, "VACIJUS", bounds, facesRight, FALLBACK);
    }
}
