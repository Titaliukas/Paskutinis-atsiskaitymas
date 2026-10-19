package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Abstract product: draws a ranged attack inside its screen bounds, facing the direction it was fired. */
interface ProjectileRenderer {
    void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight);
}
