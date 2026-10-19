package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Abstract product: draws a melee hitbox inside its screen bounds. */
interface MeleeRenderer {
    void draw(Graphics2D g, Rectangle2D bounds);
}
