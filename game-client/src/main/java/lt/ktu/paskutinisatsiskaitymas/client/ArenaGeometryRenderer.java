package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import lt.ktu.paskutinisatsiskaitymas.protocol.ArenaSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlatformSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.SidePassageSnapshot;

/** Presentation of authoritative geometry; the caller supplies the common world transform. */
final class ArenaGeometryRenderer {
    private ArenaGeometryRenderer() { }

    static void draw(Graphics2D g, ArenaSnapshot arena, boolean debug) {
        g.clip(new Rectangle2D.Double(0, 0, arena.width(), arena.height()));
        for (PlatformSnapshot p : arena.platforms()) {
            if (p.kind().equals("service")) {
                g.setColor(new Color(88, 101, 109));
                g.fill(new Rectangle2D.Double(p.x(), p.y(), p.width(), p.height()));
                g.setColor(new Color(54, 65, 72, 200));
                for (double x : new double[]{p.x() + 8, p.x() + p.width() - 12}) {
                    Path2D bracket = new Path2D.Double();
                    bracket.moveTo(x, p.y() + p.height());
                    bracket.lineTo(x + 4, p.y() + p.height());
                    bracket.lineTo(x + 4, p.y() + p.height() + 7);
                    bracket.closePath();
                    g.fill(bracket);
                }
            }
            g.setColor(new Color(35, 43, 49, 85));
            g.fill(new Rectangle2D.Double(p.x(), p.y() + 1, p.width(), 2));
            g.setColor(new Color(212, 221, 217, p.kind().equals("service") ? 220 : 125));
            g.setStroke(new BasicStroke(1));
            g.draw(new Line2D.Double(p.x(), p.y(), p.x() + p.width(), p.y()));
            g.setColor(new Color(102, 117, 124, 125));
            g.draw(new Line2D.Double(p.x(), p.y(), p.x(), p.y() + 4));
            g.draw(new Line2D.Double(p.x() + p.width(), p.y(), p.x() + p.width(), p.y() + 4));
        }
        if (arena.sidePassage() != null) {
            drawOpening(g, arena.sidePassage(), arena.width(), false);
            drawOpening(g, arena.sidePassage(), arena.width(), true);
        }
        if (debug) {
            g.setColor(Color.MAGENTA);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setStroke(new BasicStroke((float) (1 / g.getTransform().getScaleX())));
            for (PlatformSnapshot p : arena.platforms()) {
                g.draw(new Rectangle2D.Double(p.x(), p.y(), p.width(), p.height()));
            }
            if (arena.sidePassage() != null) {
                var passage = arena.sidePassage();
                g.draw(new Line2D.Double(0, passage.openingTop(), 0, passage.supportY()));
                g.draw(new Line2D.Double(arena.width() - 1, passage.openingTop(),
                        arena.width() - 1, passage.supportY()));
            }
        }
    }

    private static void drawOpening(Graphics2D g, SidePassageSnapshot passage, double arenaWidth, boolean right) {
        Graphics2D opening = (Graphics2D) g.create();
        try {
            if (right) {
                opening.translate(arenaWidth, 0);
                opening.scale(-1, 1);
            }
            double height = passage.supportY() - passage.openingTop();
            double width = height * 0.64;
            double x = -6;
            double top = passage.openingTop();
            opening.setColor(new Color(34, 43, 51));
            opening.fill(new Rectangle2D.Double(x, top, width, height));
            opening.setColor(new Color(118, 129, 135));
            opening.fill(new Rectangle2D.Double(x, top, width, 3));
            opening.fill(new Rectangle2D.Double(x + width - 3, top, 3, height));
            opening.setColor(new Color(166, 175, 179, 160));
            opening.setStroke(new BasicStroke(1));
            opening.draw(new Line2D.Double(x, top, x + width, top));
            // Muted paired chevrons point toward the edge. This is paint only, never a collision wall.
            opening.setColor(new Color(108, 141, 157));
            double cy = top + height * 0.48;
            for (double cx : new double[]{17, 26}) {
                Path2D arrow = new Path2D.Double();
                arrow.moveTo(cx + 4, cy - 5);
                arrow.lineTo(cx - 1, cy);
                arrow.lineTo(cx + 4, cy + 5);
                opening.draw(arrow);
            }
        } finally {
            opening.dispose();
        }
    }

    static boolean supported(ArenaSnapshot arena, double x, double feetY, double width) {
        return arena.platforms().stream().anyMatch(p -> Math.abs(p.y() - feetY) < 0.01
                && x + width > p.x() && x < p.x() + p.width());
    }
}
