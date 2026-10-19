package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Abstract product: draws a player's body inside its screen bounds. */
interface BodyRenderer {
    void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight);
}
