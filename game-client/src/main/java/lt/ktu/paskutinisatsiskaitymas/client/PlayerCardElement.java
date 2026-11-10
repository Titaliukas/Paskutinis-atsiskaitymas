package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlayerSnapshot;

final class PlayerCardElement implements HudElement {
    private final int slot;

    PlayerCardElement(int slot) {
        if (slot < 0 || slot > 3) {
            throw new IllegalArgumentException("Player slot must be between 0 and 3");
        }
        this.slot = slot;
    }

    int slot() {
        return slot;
    }

    @Override
    public void draw(Graphics2D g, HudContext context) {
        PlayerSnapshot player = context.snapshot().players().stream()
                .filter(value -> value.slot() == slot).findFirst().orElse(null);
        if (player == null) {
            return;
        }
        double cardScale = slot < 2 ? context.hudScale() : Math.max(0.1, context.bottomScale());
        int width = Math.min(248, (int) ((context.width() - 3 * HudStyle.CARD_MARGIN) / (2 * cardScale)));
        double margin = slot < 2 ? HudStyle.CARD_MARGIN : HudStyle.CARD_MARGIN * cardScale;
        double x = slot % 2 == 0 ? margin : context.width() - margin - width * cardScale;
        double y = slot < 2 ? margin : context.height() - margin - HudStyle.CARD_HEIGHT * cardScale;
        Graphics2D card = (Graphics2D) g.create();
        try {
            card.translate(x, y);
            card.scale(cardScale, cardScale);
            drawCard(card, player, width, context);
        } finally {
            card.dispose();
        }
    }

    private void drawCard(Graphics2D g, PlayerSnapshot player, int width, HudContext context) {
        Color accent = HudStyle.PLAYER_COLORS[player.slot()];
        boolean local = player.playerId().equals(context.localPlayerId());
        g.setColor(HudStyle.CARD_BACKGROUND);
        g.fillRoundRect(0, 0, width, HudStyle.CARD_HEIGHT, 8, 8);
        g.setColor(accent);
        g.fillRect(1, 9, 3, HudStyle.CARD_HEIGHT - 18);
        g.setColor(local ? Color.WHITE : accent);
        g.drawRoundRect(0, 0, width - 1, HudStyle.CARD_HEIGHT - 1, 8, 8);
        if (local) {
            g.drawRoundRect(1, 1, width - 3, HudStyle.CARD_HEIGHT - 3, 7, 7);
        }
        drawPortrait(g, 12, 14, 64, player.character(), context.assets());
        int textX = 88;
        int textWidth = width - 100;
        Font original = g.getFont();
        g.setFont(original.deriveFont(Font.BOLD, 13f));
        g.setColor(Color.WHITE);
        g.drawString(HudStyle.ellipsize(g, player.nickname(), textWidth), textX, 26);
        g.setFont(original.deriveFont(Font.PLAIN, 13f));
        String stress = BigDecimal.valueOf(player.stressLevel()).stripTrailingZeros().toPlainString();
        g.drawString(HudStyle.ellipsize(g, "Stress: " + stress, textWidth), textX, 49);
        g.setColor(local ? Color.WHITE : accent);
        g.setFont(original.deriveFont(Font.PLAIN, 11f));
        g.drawString("Player " + (player.slot() + 1) + (local ? " (you)" : ""), textX, 72);
        g.setFont(original);
    }

    private static void drawPortrait(Graphics2D g, int x, int y, int size, String character, AssetManager assets) {
        g.setColor(new Color(33, 42, 57));
        g.fillRect(x, y, size, size);
        Image portrait = assets.getImage(character + "_PORTRAIT");
        if (portrait != null) {
            double scale = Math.min((double) size / portrait.getWidth(null), (double) size / portrait.getHeight(null));
            int width = (int) Math.round(portrait.getWidth(null) * scale);
            int height = (int) Math.round(portrait.getHeight(null) * scale);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(portrait, x + (size - width) / 2, y + (size - height) / 2, width, height, null);
        } else {
            g.setColor(Color.LIGHT_GRAY);
            g.fillOval(x + size / 3, y + size / 8, size / 3, size / 3);
            g.fillRoundRect(x + size / 6, y + size / 2, size * 2 / 3, size * 3 / 8, 8, 8);
        }
    }
}