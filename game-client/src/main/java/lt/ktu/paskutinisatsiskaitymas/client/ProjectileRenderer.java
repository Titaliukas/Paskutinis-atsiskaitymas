package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Abstract product: draws a ranged attack inside its screen bounds. */
interface ProjectileRenderer {
    void draw(Graphics2D g, Rectangle2D bounds);
}
