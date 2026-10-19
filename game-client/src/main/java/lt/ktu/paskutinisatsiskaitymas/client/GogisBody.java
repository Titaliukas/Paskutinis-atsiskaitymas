package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Sprite of Gogis. */
final class GogisBody implements BodyRenderer {
    private static final Color FALLBACK = new Color(170, 90, 230);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight) {
        SpriteDrawing.drawOrFill(g, "GOGIS", bounds, facesRight, FALLBACK);
    }
}
