package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.UUID;
import javax.swing.JPanel;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlayerSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.ItemSnapshot;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import java.awt.geom.Ellipse2D;

/** Direct Java2D renderer for immutable authoritative snapshots. All mutation occurs on the EDT. */
final class GamePanel extends JPanel {
    private static final Color SKY = new Color(38, 48, 68);
    private static final Color PLATFORM = new Color(83, 69, 55);
    private static final Color FIRST_PLAYER = new Color(58, 141, 255);
    private static final Color SECOND_PLAYER = new Color(244, 92, 92);
    private static final Color SHIELD_RING = new Color(120, 200, 255);
    private static final double SHIELD_MARGIN = 6.0;
    private static final double SHIELD_SIZE = 46.0;
    private static final double SHIELD_VERTICAL_GAP = 10.0;
    private static final double SHIELD_HORIZONTAL_OFFSET = 14.0;

    private final Map<UUID, Boolean> facingRight = new HashMap<>();
    private final Map<String, Image> itemIcons = new HashMap<>();
    private WorldSnapshot snapshot;
    private UUID localPlayerId;

    GamePanel() {
        loadIcons();
        setPreferredSize(new Dimension(960, 540));
        setMinimumSize(new Dimension(640, 360));
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setBackground(Color.BLACK);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
            }
        });
    }

    void setLocalPlayerId(UUID playerId) {
        localPlayerId = playerId;
        repaint();
    }

    void setSnapshot(WorldSnapshot value) {
        snapshot = value;
        repaint();
    }

    void clearGame() {
        snapshot = null;
        localPlayerId = null;
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
            for (ItemSnapshot item : snapshot.items()) {
                drawItem(g, item, scale, offsetX, offsetY);
            }
        } finally {
            g.dispose();
        }
    }

    private void loadIcons() {
        loadSingleIcon("SPEED_BOOST", "assets/SPEED.png");
        loadSingleIcon("JUMP_BOOST", "assets/JUMP.png");
        loadSingleIcon("SHIELD", "assets/SHIELD.png");
    }
    private void loadSingleIcon(String key, String resourcePath) {
        try (var stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream != null) {
                itemIcons.put(key, ImageIO.read(stream));
            } else {
                System.err.println("Icon not found: " + resourcePath);
            }
        } catch (IOException e) {
            System.err.println("Loading error" + resourcePath + ": " + e.getMessage());
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

        g.setColor(player.slot() == 0 ? FIRST_PLAYER : SECOND_PLAYER);
        g.fill(body);
        boolean local = player.playerId().equals(localPlayerId);
        g.setColor(local ? Color.WHITE : new Color(25, 25, 25));
        g.setStroke(new BasicStroke(local ? 3f : 1.5f));
        g.draw(body);
        g.setColor(Color.WHITE);
        String label = player.nickname() + (local ? " (you)" : "");
        FontMetrics metrics = g.getFontMetrics();
        int labelX = (int) Math.round(body.getCenterX() - metrics.stringWidth(label) / 2.0);
        int labelY = (int) Math.max(offsetY + metrics.getAscent(), body.getY() - 6);
        g.drawString(label, labelX, labelY);
        if (player.shielded()){
            drawShieldIconAboveHead(g, body, scale, facesRight);
        }
    }
    private void drawShieldIconAboveHead(Graphics2D g, Rectangle2D body, double scale, boolean facesRight) {
        double iconSize = SHIELD_SIZE * scale;
        double horizontalOffset = (body.getWidth() / 2) * (facesRight ? 1 : -1);
        double centerX = body.getCenterX() + horizontalOffset;
        double centerY = body.getCenterY();

        Image icon = itemIcons.get("SHIELD");
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

        double itemSize = 48.0; 
        int width = (int) Math.round(itemSize * scale);
        int height = (int) Math.round(itemSize * scale);

        int x = (int) Math.round(offsetX + (item.x() - itemSize / 2.0) * scale);
        int y = (int) Math.round(offsetY + (item.y() - itemSize / 2.0) * scale);

        Image icon = itemIcons.get(item.type());

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
