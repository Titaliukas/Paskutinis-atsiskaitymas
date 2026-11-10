package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** Shared HUD constants and text helper used by the leaf elements. */
final class HudStyle {
    static final Color[] PLAYER_COLORS = {
        new Color(58, 141, 255), new Color(244, 92, 92),
        new Color(92, 201, 130), new Color(232, 183, 75)
    };
    static final int CARD_MARGIN = 12;
    static final int CARD_HEIGHT = 92;
    static final Color CARD_BACKGROUND = new Color(17, 23, 34, 225);

    private HudStyle() { }

    static String ellipsize(Graphics2D g, String text, int maximumWidth) {
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= maximumWidth) {
            return text;
        }
        String suffix = "...";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + suffix) > maximumWidth) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end) + suffix;
    }
}