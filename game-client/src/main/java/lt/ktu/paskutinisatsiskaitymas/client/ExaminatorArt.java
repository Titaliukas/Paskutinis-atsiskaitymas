package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Rectangle2D;

/** Optional cached art with small pixel-style vector fallbacks; no generated bitmap files. */
final class ExaminatorArt {
    static final String[] KEYS = {"EXAMINATOR_IDLE", "EXAMINATOR_SCAN", "EXAMINATOR_FIRE", "EXAMINATOR_ICON",
            "EXAMINATOR_EXAM_PAPER", "EXAMINATOR_RED_PEN", "EXAMINATOR_IMPACT"};
    private ExaminatorArt() { }

    static void draw(Graphics2D graphics, AssetManager assets, String key, Rectangle2D bounds) {
        Image image = assets.getImage(key);
        if (image != null) {
            double scale = Math.min(bounds.getWidth() / image.getWidth(null), bounds.getHeight() / image.getHeight(null));
            double width = image.getWidth(null) * scale;
            double height = image.getHeight(null) * scale;
            SpriteDrawing.draw(graphics, image, new Rectangle2D.Double(bounds.getCenterX() - width / 2,
                    bounds.getCenterY() - height / 2, width, height), true);
            return;
        }
        fallback(graphics, key, bounds);
    }

    static void fallback(Graphics2D graphics, String key, Rectangle2D bounds) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(bounds.getX(), bounds.getY());
            double unit = Math.min(bounds.getWidth(), bounds.getHeight()) / 32;
            g.scale(unit, unit);
            if (key.equals("EXAMINATOR_EXAM_PAPER")) {
                g.setColor(new Color(250, 240, 208));
                g.fillRect(6, 3, 20, 26);
                g.setColor(new Color(54, 71, 91));
                for (int y = 9; y < 26; y += 5) { g.fillRect(10, y, 12, 2); }
                g.setColor(new Color(202, 48, 62));
                g.fillRect(19, 22, 5, 3);
            } else if (key.equals("EXAMINATOR_RED_PEN")) {
                g.setColor(new Color(202, 48, 62));
                g.fillRect(13, 3, 6, 23);
                g.setColor(new Color(250, 240, 208));
                g.fillRect(14, 26, 4, 4);
            } else if (key.equals("EXAMINATOR_IMPACT")) {
                g.setColor(new Color(255, 102, 95));
                g.fillRect(3, 14, 26, 4);
                g.fillRect(14, 3, 4, 26);
                g.fillRect(7, 7, 5, 5);
                g.fillRect(20, 20, 5, 5);
            } else {
                // Academic inspection drone: rotor arms, blue body, red scanner and mortarboard.
                g.setColor(new Color(39, 48, 67));
                g.fillRect(2, 11, 28, 4);
                g.fillRect(0, 8, 10, 3);
                g.fillRect(22, 8, 10, 3);
                g.setColor(new Color(135, 195, 213));
                g.fillRect(8, 12, 16, 12);
                g.setColor(key.equals("EXAMINATOR_FIRE") ? new Color(255, 85, 82) : new Color(91, 255, 214));
                g.fillRect(12, 17, 8, 5);
                g.setColor(new Color(39, 48, 67));
                g.fillRect(9, 5, 14, 4);
                g.fillRect(5, 3, 22, 3);
                g.setColor(new Color(255, 209, 92));
                g.fillRect(24, 5, 2, 6);
            }
        } finally {
            g.dispose();
        }
    }
}
