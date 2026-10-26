package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Arc2D;
import java.awt.geom.Rectangle2D;

/** Jurgelinis strikes with UML stick man. The 32x32 art faces right and is mirrored for leftward strikes. */
final class JurgelinisMelee implements MeleeRenderer {
    private static final Color AREA = new Color(90, 200, 110, 70);
    private static final Color SLASH = new Color(90, 200, 110);

    @Override
    public void draw(Graphics2D g, Rectangle2D bounds, boolean facesRight) {
        Image image = AssetManager.getInstance().getImage("JURGELINIS_MELEE");
        if (image != null) {
            double size = Math.min(bounds.getWidth(), bounds.getHeight());
            SpriteDrawing.drawCentered(g, image, bounds, size, facesRight);
            return;
        }
        Graphics2D slash = (Graphics2D) g.create();
        try {
            slash.setColor(AREA);
            slash.fill(bounds);
            slash.setColor(SLASH);
            slash.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            slash.draw(new Arc2D.Double(bounds, 300, 120, Arc2D.OPEN));
            slash.draw(new Arc2D.Double(bounds, 120, 120, Arc2D.OPEN));
        } finally {
            slash.dispose();
        }
    }
}
