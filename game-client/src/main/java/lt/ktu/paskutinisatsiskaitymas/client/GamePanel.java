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

/** Direct Java2D renderer for immutable authoritative snapshots. All mutation occurs on the EDT. */
final class GamePanel extends JPanel {
    private static final Color SKY = new Color(38, 48, 68);
    private static final Color PLATFORM = new Color(83, 69, 55);
    private static final Color FIRST_PLAYER = new Color(58, 141, 255);
    private static final Color SECOND_PLAYER = new Color(244, 92, 92);
    private WorldSnapshot snapshot;
    private UUID localPlayerId;

    GamePanel() {
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
    }

    private void drawCentered(Graphics2D g, String text, int width, int height) {
        g.setColor(Color.LIGHT_GRAY);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, (width - metrics.stringWidth(text)) / 2,
                (height + metrics.getAscent()) / 2);
    }
}
