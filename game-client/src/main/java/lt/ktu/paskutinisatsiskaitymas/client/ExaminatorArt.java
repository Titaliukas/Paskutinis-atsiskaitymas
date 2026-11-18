package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.Rectangle2D;

/** Cached normalized heads and laser strips; all dimensions are world units. */
final class ExaminatorArt {
    static final String[] DIRECTIONS = {"RIGHT", "DOWN_RIGHT", "DOWN", "DOWN_LEFT", "LEFT", "UP_LEFT", "UP", "UP_RIGHT"};
    static final String[] KEYS = {"EXAMINATOR_IDLE", "EXAMINATOR_ARRIVE", "EXAMINATOR_HOVER", "EXAMINATOR_LEFT",
            "EXAMINATOR_RIGHT", "EXAMINATOR_ATTACK_LEFT", "EXAMINATOR_ATTACK_UP_LEFT", "EXAMINATOR_ATTACK_UP",
            "EXAMINATOR_ATTACK_UP_RIGHT", "EXAMINATOR_ATTACK_RIGHT", "EXAMINATOR_ATTACK_DOWN_RIGHT",
            "EXAMINATOR_ATTACK_DOWN", "EXAMINATOR_ATTACK_DOWN_LEFT", "EXAMINATOR_LASER", "EXAMINATOR_LASER_SOURCE",
            "EXAMINATOR_LASER_MIDDLE", "EXAMINATOR_LASER_TIP"};
    private ExaminatorArt() { }

    static String direction(double dx, double dy) {
        int sector = (int) Math.round(Math.atan2(dy, dx) / (Math.PI / 4));
        return DIRECTIONS[Math.floorMod(sector, 8)];
    }

    /** Clean scan heads aim without premature discharge; isolated directional head/flash poses fire. */
    static String cleanAimPose(double dx, double dy) {
        String direction = direction(dx, dy);
        return "EXAMINATOR_" + (direction.contains("LEFT") ? "LEFT" : "RIGHT");
    }

    static void draw(Graphics2D graphics, AssetManager assets, String key, Rectangle2D bounds) {
        Image image = assets.getImage(key);
        if (image != null) {
            double scale = Math.min(bounds.getWidth() / image.getWidth(null), bounds.getHeight() / image.getHeight(null));
            double width = image.getWidth(null) * scale;
            double height = image.getHeight(null) * scale;
            SpriteDrawing.draw(graphics, image, new Rectangle2D.Double(bounds.getCenterX() - width / 2,
                    bounds.getCenterY() - height / 2, width, height), true);
        } else {
            fallback(graphics, key, bounds);
        }
    }

    /** Source flare and tip keep their aspect; only the middle stretches. Zero-length shots are harmless. */
    static void laser(Graphics2D graphics, AssetManager assets, double x, double y, double endX, double endY) {
        double distance = Math.hypot(endX - x, endY - y);
        if (distance < 0.001) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            g.rotate(Math.atan2(endY - y, endX - x));
            Image source = assets.getImage("EXAMINATOR_LASER_SOURCE");
            Image middle = assets.getImage("EXAMINATOR_LASER_MIDDLE");
            Image tip = assets.getImage("EXAMINATOR_LASER_TIP");
            if (source == null || middle == null || tip == null) {
                g.setColor(new Color(255, 40, 40, 180));
                g.fill(new Rectangle2D.Double(0, -3, distance, 6));
                g.setColor(Color.WHITE);
                g.fill(new Rectangle2D.Double(0, -0.8, distance, 1.6));
            } else {
                double cap = Math.min(10, distance / 2);
                // Source center in its crop is x=87/139. Place that exact flare at the emitter.
                double sourceLeft = -cap * 87 / 139;
                SpriteDrawing.draw(g, source, new Rectangle2D.Double(sourceLeft, -5, cap, 10), true);
                double middleLeft = sourceLeft + cap;
                SpriteDrawing.draw(g, middle, new Rectangle2D.Double(middleLeft, -5,
                        Math.max(0.001, distance - cap - middleLeft), 10), true);
                SpriteDrawing.draw(g, tip, new Rectangle2D.Double(distance - cap, -5, cap, 10), true);
            }
        } finally { g.dispose(); }
    }

    static void fallback(Graphics2D graphics, String key, Rectangle2D bounds) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(bounds.getX(), bounds.getY());
            double unit = Math.min(bounds.getWidth(), bounds.getHeight()) / 32;
            g.scale(unit, unit);
            g.setColor(new Color(38, 40, 49));
            g.fillRect(6, 2, 20, 27);
            g.setColor(new Color(230, 165, 132));
            g.fillRect(8, 5, 10, 20);
            g.setColor(new Color(147, 155, 167));
            g.fillRect(18, 5, 7, 20);
            g.setColor(new Color(255, 45, 45));
            g.fillRect(20, 13, 4, 3);
            g.setColor(Color.WHITE);
            g.fillRect(10, 20, 7, 2);
        } finally { g.dispose(); }
    }
}
