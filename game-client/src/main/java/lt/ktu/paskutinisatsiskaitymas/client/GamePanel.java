package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.swing.JPanel;
import lt.ktu.paskutinisatsiskaitymas.protocol.ItemSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.NPCSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlayerSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** Direct Java2D renderer for immutable authoritative snapshots. All mutation occurs on the EDT. */
final class GamePanel extends JPanel {
    private static final Color SKY = new Color(38, 48, 68);
    private static final Color PLATFORM = new Color(83, 69, 55);
    private static final Color[] PLAYER_COLORS = {
        new Color(58, 141, 255), new Color(244, 92, 92),
        new Color(92, 201, 130), new Color(232, 183, 75)
    };
    private static final int CARD_MARGIN = 12;
    private static final int CARD_HEIGHT = 92;
    private static final Color CARD_BACKGROUND = new Color(17, 23, 34, 225);
    private static final Color SHIELD_RING = new Color(120, 200, 255);
    private static final double SHIELD_SIZE = 32.0;
    private final Map<UUID, Boolean> facingRight = new HashMap<>();
    private final AssetManager assets = AssetManager.getInstance();
    private WorldSnapshot snapshot;
    private UUID localPlayerId;
    private String notification;
    private long notificationUntil;
    private final NPCPresentation npcPresentation = new NPCPresentation();
    // Reuse the existing UI timer for notification expiry and the short presentation-only attack latch.
    private final javax.swing.Timer notificationTimer = new javax.swing.Timer(30, event -> refreshPresentation());

    private void refreshPresentation() {
        boolean notificationActive = notification != null && System.nanoTime() - notificationUntil < 0;
        if (!notificationActive) {
            notification = null;
        }
        if (!notificationActive && !npcPresentation.hasActiveLatch()) {
            notificationTimer.stop();
        }
        repaint();
    }



    GamePanel() {
        setPreferredSize(new Dimension(960, 540));
        setMinimumSize(new Dimension(640, 360));
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setBackground(Color.BLACK);
        notificationTimer.setRepeats(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
            }
        });
    }

    void showNotification(String text) {
        notification = text;
        notificationUntil = System.nanoTime() + 4_000_000_000L;
        notificationTimer.restart();
        repaint();
    }

    void setLocalPlayerId(UUID playerId) {
        localPlayerId = playerId;
        repaint();
    }

    void setSnapshot(WorldSnapshot value) {
        snapshot = value;
        npcPresentation.update(value.npcs());
        if (npcPresentation.hasActiveLatch()) {
            notificationTimer.start();
        }
        repaint();
    }

    void clearGame() {
        snapshot = null;
        npcPresentation.clear();
        facingRight.clear();
        notificationTimer.stop();
        localPlayerId = null;
        notification = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (snapshot == null) {
                drawCentered(g, "Connect to start", getWidth(), getHeight());
                return;
            }
            double scale = Math.min(getWidth() / snapshot.arena().width(),
                    getHeight() / snapshot.arena().height());
            double arenaWidth = snapshot.arena().width() * scale;
            double arenaHeight = snapshot.arena().height() * scale;
            double offsetX = (getWidth() - arenaWidth) / 2.0;
            double offsetY = (getHeight() - arenaHeight) / 2.0;

            g.setColor(SKY);
            g.fill(new Rectangle2D.Double(offsetX, offsetY, arenaWidth, arenaHeight));
            g.setColor(PLATFORM);
            snapshot.arena().platforms().forEach(platform -> g.fill(new Rectangle2D.Double(
                    offsetX + platform.x() * scale,
                    offsetY + platform.y() * scale,
                    platform.width() * scale,
                    platform.height() * scale)));
            for (PlayerSnapshot player : snapshot.players()) {
                drawPlayer(g, player, scale, offsetX, offsetY);
            }
            for (NPCSnapshot npc : snapshot.npcs()) {
                drawNPC(g, npc, scale, offsetX, offsetY);
            }
            for (ItemSnapshot item : snapshot.items()) {
                drawItem(g, item, scale, offsetX, offsetY);
            }
            drawPlayerCards(g);
            drawNotification(g);
        } finally {
            g.dispose();
        }
    }

    private void drawPlayer(Graphics2D g, PlayerSnapshot player, double scale, double offsetX, double offsetY) {
        Rectangle2D body = new Rectangle2D.Double(
                offsetX + player.x() * scale,
                offsetY + player.y() * scale,
                player.width() * scale,
                player.height() * scale);
        if (player.velocityX() > 0.01) {
            facingRight.put(player.playerId(), true);
        } else if (player.velocityX() < -0.01) {
            facingRight.put(player.playerId(), false);
        }
        boolean facesRight = facingRight.getOrDefault(player.playerId(), true);

        Image characterIcon = assets.getImage("MARTY");
        if (characterIcon != null) {
            drawCharacterImage(g, characterIcon, body, facesRight);
        } else {
            g.setColor(PLAYER_COLORS[player.slot()]);
            g.fill(body);
        }

        g.setColor(Color.WHITE);
        String label = player.nickname() + (player.playerId().equals(localPlayerId) ? " (you)" : "");
        g.drawString(label, (int) body.getX(), (int) body.getY() - 6);
        if (player.playerId().equals(localPlayerId)) {
            g.draw(body);
        }
        if (player.shielded()) {
            drawShieldIconAboveHead(g, body, scale, facesRight);
        }
    }

    /** Cards use panel coordinates and stable slots, independently of list order and world movement. */
    private void drawPlayerCards(Graphics2D g) {
        int width = Math.min(248, (getWidth() - 3 * CARD_MARGIN) / 2);
        for (PlayerSnapshot player : snapshot.players()) {
            int x = player.slot() % 2 == 0 ? CARD_MARGIN : getWidth() - CARD_MARGIN - width;
            int y = player.slot() < 2 ? CARD_MARGIN : getHeight() - CARD_MARGIN - CARD_HEIGHT;
            drawPlayerCard(g, player, x, y, width);
        }
    }

    private void drawPlayerCard(Graphics2D g, PlayerSnapshot player, int x, int y, int width) {
        Color accent = PLAYER_COLORS[player.slot()];
        boolean local = player.playerId().equals(localPlayerId);
        g.setColor(CARD_BACKGROUND);
        g.fillRoundRect(x, y, width, CARD_HEIGHT, 8, 8);
        g.setColor(accent);
        g.fillRect(x + 1, y + 9, 3, CARD_HEIGHT - 18);
        g.setColor(local ? Color.WHITE : accent);
        g.drawRoundRect(x, y, width - 1, CARD_HEIGHT - 1, 8, 8);
        if (local) {
            g.drawRoundRect(x + 1, y + 1, width - 3, CARD_HEIGHT - 3, 7, 7);
        }
        drawPortrait(g, x + 12, y + 14, 64);
        int textX = x + 88;
        int textWidth = width - 100;
        Font original = g.getFont();
        g.setFont(original.deriveFont(Font.BOLD, 13f));
        g.setColor(Color.WHITE);
        g.drawString(ellipsize(g, player.nickname(), textWidth), textX, y + 26);
        g.setFont(original.deriveFont(Font.PLAIN, 13f));
        String stress = BigDecimal.valueOf(player.stressLevel()).stripTrailingZeros().toPlainString();
        g.drawString(ellipsize(g, "Stress: " + stress, textWidth), textX, y + 49);
        g.setColor(local ? Color.WHITE : accent);
        g.setFont(original.deriveFont(Font.PLAIN, 11f));
        g.drawString("Player " + (player.slot() + 1) + (local ? " (you)" : ""), textX, y + 72);
        g.setFont(original);
    }

    private void drawPortrait(Graphics2D g, int x, int y, int size) {
        g.setColor(new Color(33, 42, 57));
        g.fillRect(x, y, size, size);
        Image portrait = assets.getImage("MARTY_PORTRAIT");
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

    private void drawNotification(Graphics2D g) {
        if (notification == null) {
            return;
        }
        String text = ellipsize(g, notification, getWidth() - 2 * CARD_MARGIN - 16);
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text) + 16;
        int x = (getWidth() - width) / 2;
        int y = CARD_MARGIN + CARD_HEIGHT + 10;
        g.setColor(CARD_BACKGROUND);
        g.fillRoundRect(x, y, width, metrics.getHeight() + 8, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 8, y + 4 + metrics.getAscent());
    }

    private String ellipsize(Graphics2D g, String text, int maximumWidth) {
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

    private void drawNPC(Graphics2D g, NPCSnapshot npc, double scale, double offsetX, double offsetY) {
        NPCPresentation.PoseStyle style = npcPresentation.style(npc);
        Image image = assets.getImage(style.assetKey());
        if (image == null) {
            style = NPCPresentation.patrolStyle();
            image = assets.getImage(style.assetKey());
        }
        if (image != null) {
            Rectangle2D bounds = style.bounds(npc, image, scale, offsetX, offsetY);
            drawCharacterImage(g, image, bounds, npc.facingRight());
        } else {
            g.setColor(new Color(92, 201, 130));
            g.fill(new Rectangle2D.Double(offsetX + npc.x() * scale, offsetY + npc.y() * scale,
                    npc.width() * scale, npc.height() * scale));
        }
        g.setColor(new Color(150, 240, 175));
        // The label always reports server activity, even during a latched ATTACK image.
        g.drawString("NPC " + npc.id() + " " + npc.activity(),
                (int) (offsetX + npc.x() * scale), (int) (offsetY + npc.y() * scale) - 7);
    }

    @Override
    public void removeNotify() {
        notificationTimer.stop();
        super.removeNotify();
    }

    private void drawCharacterImage(Graphics2D g, Image icon, Rectangle2D body, boolean facesRight) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int x = (int) Math.round(body.getX());
        int y = (int) Math.round(body.getY());
        int width = (int) Math.round(body.getWidth());
        int height = (int) Math.round(body.getHeight());

        if (facesRight) {
            g.drawImage(icon, x, y, width, height, null);
        } else {
            g.drawImage(icon, x + width, y, -width, height, null);
        }
    }

    private void drawShieldIconAboveHead(Graphics2D g, Rectangle2D body, double scale, boolean facesRight) {
        double iconSize = SHIELD_SIZE * scale;
        double horizontalOffset = (body.getWidth() / 2) * (facesRight ? 1 : -1);
        double centerX = body.getCenterX() + horizontalOffset;
        double centerY = body.getCenterY();

        Image icon = assets.getImage("SHIELD");
        if (icon != null) {
            g.drawImage(icon, (int) Math.round(centerX - iconSize / 2),
                    (int) Math.round(centerY - iconSize / 2),
                    (int) Math.round(iconSize), (int) Math.round(iconSize), null);
        } else {
            g.setColor(SHIELD_RING);
            g.fill(new Ellipse2D.Double(centerX - iconSize / 2, centerY - iconSize / 2, iconSize, iconSize));
        }
    }
    private void drawItem(Graphics2D g2d, ItemSnapshot item, double scale, double offsetX, double offsetY) {
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        double itemSize = 32.0; 
        int width = (int) Math.round(itemSize * scale);
        int height = (int) Math.round(itemSize * scale);

        int x = (int) Math.round(offsetX + (item.x() - itemSize / 2.0) * scale);
        int y = (int) Math.round(offsetY + (item.y() - itemSize / 2.0) * scale);

        Image icon = assets.getImage(item.type());

        if (icon != null) {
            g2d.drawImage(icon, x, y, width, height, null);
        } else {
            g2d.setColor(Color.YELLOW);
            g2d.fill(new Ellipse2D.Double(x, y, width, height));
        }
    }

    private void drawCentered(Graphics2D g, String text, int width, int height) {
        g.setColor(Color.LIGHT_GRAY);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, (width - metrics.stringWidth(text)) / 2,
                (height + metrics.getAscent()) / 2);
    }
}
