package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Sprite of Patasijus. */
final class PatasijusBody implements BodyRenderer {
    private static final Color FALLBACK = new Color(255, 150, 40);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight) {
        SpriteDrawing.drawOrFill(g, "PATASIJUS", bounds, facesRight, FALLBACK);
    }
}
