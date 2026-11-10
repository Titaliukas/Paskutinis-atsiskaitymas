package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** Leaf: the short-lived notification banner near the bottom of the arena. */
final class NotificationElement implements HudElement {
    @Override
    public void draw(Graphics2D g, HudContext context) {
        if (context.notification() == null) {
            return;
        }
        String text = HudStyle.ellipsize(g, context.notification(),
                context.width() - 2 * HudStyle.CARD_MARGIN - 16);
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text) + 16;
        int height = metrics.getHeight() + 8;
        double scale = context.worldScale();
        int x = (int) Math.round(context.offsetX() + 480 * scale - width / 2.0);
        int y = (int) Math.round(context.offsetY() + 480 * scale - height / 2.0);
        g.setColor(HudStyle.CARD_BACKGROUND);
        g.fillRoundRect(x, y, width, height, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 8, y + 4 + metrics.getAscent());
    }
}