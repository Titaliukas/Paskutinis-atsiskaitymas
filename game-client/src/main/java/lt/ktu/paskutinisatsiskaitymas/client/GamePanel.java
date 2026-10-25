package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.geom.AffineTransform;
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
import lt.ktu.paskutinisatsiskaitymas.protocol.ProjectileSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.AttackSnapshot;

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
    private static final double SHIELD_SIZE = 20.0;
    private static final Color PROJECTILE = new Color(255, 190, 60);
    private static final Color MELEE_SWING = new Color(255, 255, 255, 150);
    private static final Color HEALTH_BACK = new Color(40, 40, 40, 200);
    private static final Color HEALTH_FILL = new Color(90, 220, 110);
    private final Map<UUID, Boolean> facingRight = new HashMap<>();
    private final Map<String, CharacterSkin> skins = new HashMap<>();
    private final boolean debugGeometry = Boolean.getBoolean("game.debug.geometry");
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
                drawCentered(g, "Waiting for arena\u2026", getWidth(), getHeight());
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
            Image background = assets.arenaBackground(snapshot.arena().arenaType());
            if (background != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                AffineTransform transform = AffineTransform.getTranslateInstance(offsetX, offsetY);
                transform.scale(arenaWidth / background.getWidth(null), arenaHeight / background.getHeight(null));
                g.drawImage(background, transform, null);
            }
            if (background == null) {
                g.setColor(PLATFORM);
                g.setStroke(new BasicStroke(1));
                snapshot.arena().platforms().forEach(platform -> {
                    Rectangle2D bounds = new Rectangle2D.Double(offsetX + platform.x() * scale,
                            offsetY + platform.y() * scale, platform.width() * scale, platform.height() * scale);
                    g.fill(bounds);
                });
            }
            Graphics2D world = (Graphics2D) g.create();
            try {
                world.translate(offsetX, offsetY);
                world.scale(scale, scale);
                ArenaGeometryRenderer.draw(world, snapshot.arena(), debugGeometry);
            } finally {
                world.dispose();
            }
            for (PlayerSnapshot player : snapshot.players()) {
                drawPlayer(g, player, scale, offsetX, offsetY);
            }
            for (NPCSnapshot npc : snapshot.npcs()) {
                drawNPC(g, npc, scale, offsetX, offsetY);
            }
            for (ItemSnapshot item : snapshot.items()) {
                drawItem(g, item, scale, offsetX, offsetY);
            }
            for (AttackSnapshot attack : snapshot.attacks()) {
                drawAttack(g, attack, scale, offsetX, offsetY);
            }
            for (ProjectileSnapshot projectile : snapshot.projectiles()) {
                drawProjectile(g, projectile, scale, offsetX, offsetY);
            }
            drawPlayerCards(g);
            drawNotification(g, scale, offsetX, offsetY);
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

        if (player.grounded() && ArenaGeometryRenderer.supported(snapshot.arena(), player.x(),
                player.y() + player.height(), player.width())) {
            drawContactShadow(g, body);
        }
        CharacterSkin skin = skinFor(player.character());
        if (skin != null) {
            skin.body().draw(g, body, facesRight);
        } else {
            g.setColor(PLAYER_COLORS[player.slot()]);
            g.fill(body);
        }

        g.setColor(Color.WHITE);
        String label = player.nickname() + (player.playerId().equals(localPlayerId) ? " (you)" : "");
        g.drawString(label, (int) body.getX(), (int) body.getY() - 6);
        if (player.shielded()) {
            drawShieldIconAboveHead(g, body, scale, facesRight);
        }
        if (player.slowed()) {
            g.setColor(new Color(255, 210, 100));
            g.drawString("\u22125% speed", (int) body.getX(), (int) body.getMaxY() + 14);
        }
        drawHealthBar(g, body, player.health(), player.maxHealth());
    }

    /** The attacker's skin draws the attack; attacks from departed or unknown characters keep the plain shapes. */
    private void drawAttack(Graphics2D g, AttackSnapshot attack, double scale, double offsetX, double offsetY) {
        Rectangle2D bounds = new Rectangle2D.Double(
                offsetX + attack.x() * scale,
                offsetY + attack.y() * scale,
                attack.width() * scale,
                attack.height() * scale);
        boolean ranged = "RANGED".equals(attack.type());
        boolean facesRight = attack.direction() > 0;
        CharacterSkin skin = snapshot.players().stream()
                .filter(player -> player.playerId().equals(attack.sourceId()))
                .findFirst()
                .map(player -> skinFor(player.character()))
                .orElse(null);
        if (skin != null && ranged) {
            skin.projectile().draw(g, bounds, facesRight);
        } else if (skin != null) {
            skin.melee().draw(g, bounds, facesRight);
        } else if (ranged) {
            g.setColor(PROJECTILE);
            g.fill(new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight()));
        } else {
            g.setColor(MELEE_SWING);
            g.fill(bounds);
        }
    }

    /** Builds each character's renderer family once, from that character's factory; null for unknown names. */
    private CharacterSkin skinFor(String character) {
        CharacterFactory factory = CharacterFactories.forCharacter(character);
        return factory == null ? null : skins.computeIfAbsent(character, name -> CharacterSkin.from(factory));
    }

    private void drawHealthBar(Graphics2D g, Rectangle2D body, int health, int maxHealth) {
        double barHeight = 5.0;
        double barY = body.getY() - 22.0;
        double fraction = (double) health / maxHealth;
        g.setColor(HEALTH_BACK);
        g.fill(new Rectangle2D.Double(body.getX(), barY, body.getWidth(), barHeight));
        g.setColor(HEALTH_FILL);
        g.fill(new Rectangle2D.Double(body.getX(), barY, body.getWidth() * fraction, barHeight));
    }

    /** Cards use panel coordinates and stable slots, independently of list order and world movement. */
    private void drawPlayerCards(Graphics2D g) {
        double worldScale = Math.min(getWidth() / snapshot.arena().width(), getHeight() / snapshot.arena().height());
        double hudScale = Math.min(1, worldScale);
        double groundY = snapshot.arena().platforms().stream()
                .filter(p -> p.x() == 0 && p.width() == snapshot.arena().width())
                .mapToDouble(p -> p.y()).max().orElse(0);
        double offsetY = (getHeight() - snapshot.arena().height() * worldScale) / 2;
        double belowGround = getHeight() - offsetY - groundY * worldScale;
        // Keep the bottom cards entirely in the decorative base, including a margin above and below.
        double bottomScale = Math.min(hudScale, belowGround / (CARD_HEIGHT + 2.0 * CARD_MARGIN));
        for (PlayerSnapshot player : snapshot.players()) {
            double cardScale = player.slot() < 2 ? hudScale : Math.max(0.1, bottomScale);
            int width = Math.min(248, (int) ((getWidth() - 3 * CARD_MARGIN) / (2 * cardScale)));
            double margin = player.slot() < 2 ? CARD_MARGIN : CARD_MARGIN * cardScale;
            double x = player.slot() % 2 == 0 ? margin : getWidth() - margin - width * cardScale;
            double y = player.slot() < 2 ? margin : getHeight() - margin - CARD_HEIGHT * cardScale;
            Graphics2D card = (Graphics2D) g.create();
            try {
                card.translate(x, y);
                card.scale(cardScale, cardScale);
                drawPlayerCard(card, player, 0, 0, width);
            } finally {
                card.dispose();
            }
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
        drawPortrait(g, x + 12, y + 14, 64, player.character());
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

    private void drawPortrait(Graphics2D g, int x, int y, int size, String character) {
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

    private void drawNotification(Graphics2D g, double scale, double offsetX, double offsetY) {
        if (notification == null) {
            return;
        }
        String text = ellipsize(g, notification, getWidth() - 2 * CARD_MARGIN - 16);
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text) + 16;
        int height = metrics.getHeight() + 8;
        int x = (int) Math.round(offsetX + 480 * scale - width / 2.0);
        int y = (int) Math.round(offsetY + 480 * scale - height / 2.0);
        g.setColor(CARD_BACKGROUND);
        g.fillRoundRect(x, y, width, height, 6, 6);
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
        if (ArenaGeometryRenderer.supported(snapshot.arena(), npc.x(), npc.y() + npc.height(), npc.width())) {
            drawContactShadow(g, new Rectangle2D.Double(offsetX + npc.x() * scale, offsetY + npc.y() * scale,
                    npc.width() * scale, npc.height() * scale));
        }
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
        g.drawString("NPC " + npc.id() + " " + (npc.stunned() ? "STUNNED" : npc.activity()),
                (int) (offsetX + npc.x() * scale), (int) (offsetY + npc.y() * scale) - 7);
    }

    @Override
    public void removeNotify() {
        notificationTimer.stop();
        super.removeNotify();
    }

    private void drawProjectile(Graphics2D graphics, ProjectileSnapshot projectile,
            double scale, double offsetX, double offsetY) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(offsetX + projectile.x() * scale, offsetY + projectile.y() * scale);
            g.rotate(Math.atan2(projectile.velocityY(), projectile.velocityX()));
            g.scale(scale, scale);
            if (projectile.projectileType().equals("BOOK")) {
                g.setColor(new Color(170, 65, 60));
                g.fillRoundRect(-7, -8, 14, 16, 2, 2);
                g.setColor(new Color(255, 243, 210));
                g.fillRect(-4, -5, 9, 11);
                g.setColor(new Color(110, 42, 40));
                g.drawLine(-5, -7, -5, 7);
            } else {
                g.setColor(new Color(255, 243, 210));
                g.fillRoundRect(-9, -6, 18, 12, 5, 5);
                g.setColor(new Color(176, 141, 85));
                g.drawOval(-9, -6, 4, 12);
                g.drawOval(5, -6, 4, 12);
                g.setColor(new Color(190, 55, 55));
                g.fillRect(-2, -6, 4, 12);
                g.fillPolygon(new int[]{-2, 0, 3}, new int[]{4, 10, 4}, 3);
            }
            g.setColor(projectile.returning() ? Color.CYAN : new Color(25, 25, 35));
            g.drawRoundRect(-10, -10, 20, 20, 5, 5);
        } finally {
            g.dispose();
        }
    }

    private void drawCharacterImage(Graphics2D g, Image icon, Rectangle2D body, boolean facesRight) {
        SpriteDrawing.draw(g, icon, body, facesRight);
    }

    private void drawContactShadow(Graphics2D g, Rectangle2D body) {
        g.setColor(new Color(13, 20, 24, 65));
        double height = body.getHeight() / 16;
        g.fill(new Ellipse2D.Double(body.getCenterX() - body.getWidth() * 0.34,
                body.getMaxY() - height / 2, body.getWidth() * 0.68, height));
    }

    private void drawShieldIconAboveHead(Graphics2D g, Rectangle2D body, double scale, boolean facesRight) {
        double iconSize = SHIELD_SIZE * scale;
        double centerX = body.getCenterX() + body.getWidth() * 0.75 * (facesRight ? 1 : -1);
        double centerY = body.getY() - iconSize / 2 - 3 * scale;

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

        double itemSize = 22.0;
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
