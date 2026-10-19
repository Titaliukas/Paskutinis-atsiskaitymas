package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;

/** Shared sprite drawing for bodies and NPCs: mirrors the image when the figure faces left. */
final class SpriteDrawing {
    private SpriteDrawing() { }

    static void draw(Graphics2D g, Image image, Rectangle2D bounds, boolean facesRight) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int x = (int) Math.round(bounds.getX());
        int y = (int) Math.round(bounds.getY());
        int width = (int) Math.round(bounds.getWidth());
        int height = (int) Math.round(bounds.getHeight());
        if (facesRight) {
            g.drawImage(image, x, y, width, height, null);
        } else {
            g.drawImage(image, x + width, y, -width, height, null);
        }
    }

    /** Draws a square sprite of the given screen size centred on the bounds, so pixel art keeps its proportions. */
    static void drawCentered(Graphics2D g, Image image, Rectangle2D bounds, double size, boolean facesRight) {
        draw(g, image, new Rectangle2D.Double(bounds.getCenterX() - size / 2, bounds.getCenterY() - size / 2,
                size, size), facesRight);
    }

    /** Draws the character sprite, or a plain rectangle in the character colour when the image is missing. */
    static void drawOrFill(Graphics2D g, String assetKey, Rectangle2D bounds, boolean facesRight, Color fallback) {
        Image image = AssetManager.getInstance().getImage(assetKey);
        if (image != null) {
            draw(g, image, bounds, facesRight);
        } else {
            g.setColor(fallback);
            g.fill(bounds);
        }
    }
}
