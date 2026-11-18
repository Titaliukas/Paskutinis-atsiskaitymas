package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import lt.ktu.paskutinisatsiskaitymas.protocol.ExaminatorSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.ShotEndpoint;

/** EDT-owned cosmetics. Only server-recorded endpoints are drawn; a repeated snapshot never restarts a volley. */
final class ExaminatorPresentation {
    private final LongSupplier clock;
    private final Map<UUID, Long> lastVolley = new HashMap<>();
    private final Map<UUID, Volley> volleys = new HashMap<>();
    private long scanUntil;

    ExaminatorPresentation() { this(System::nanoTime); }
    ExaminatorPresentation(LongSupplier clock) { this.clock = clock; }

    void update(List<ExaminatorSnapshot> states) {
        long now = clock.getAsLong();
        scanUntil = now;
        for (ExaminatorSnapshot state : states) {
            if (state.phase().equals("SCAN")) {
                scanUntil = Math.max(scanUntil, now + (long) (state.phaseRemaining() * 1e9));
            }
            long previous = lastVolley.getOrDefault(state.ownerId(), 0L);
            if (state.volleySequence() > previous) {
                lastVolley.put(state.ownerId(), state.volleySequence());
                if (state.phase().equals("FIRE")) {
                    volleys.put(state.ownerId(), new Volley(state.originX(), state.originY(), state.endpoints(),
                            now, now + (long) (state.phaseRemaining() * 1e9)));
                }
            }
        }
        // Disconnected owners must not leave feedback, identities, or animation work behind.
        lastVolley.keySet().removeIf(id -> states.stream().noneMatch(state -> state.ownerId().equals(id)));
        volleys.keySet().retainAll(lastVolley.keySet());
        expire(now);
    }

    boolean isActive() {
        long now = clock.getAsLong();
        expire(now);
        return now < scanUntil || !volleys.isEmpty();
    }

    int volleyCount() { expire(clock.getAsLong()); return volleys.size(); }

    void clear() { lastVolley.clear(); volleys.clear(); scanUntil = 0; }

    private void expire(long now) { volleys.values().removeIf(volley -> now >= volley.until()); }

    void draw(Graphics2D g, AssetManager assets, List<ExaminatorSnapshot> states) {
        long now = clock.getAsLong();
        expire(now);
        for (ExaminatorSnapshot state : states) {
            Rectangle2D drone = new Rectangle2D.Double(state.x() - 22, state.y() - 22, 44, 44);
            ExaminatorArt.draw(g, assets, "EXAMINATOR_" + state.phase(), drone);
            if (state.phase().equals("SCAN")) {
                g.setColor(new Color(91, 255, 214, 85));
                double sweep = Math.sin(now / 80_000_000.0) * 22;
                g.draw(new Line2D.Double(state.x(), state.y() + 10, state.x() + sweep, state.y() + 65));
            }
            g.setColor(Color.WHITE);
            g.drawString(state.cooldownRemaining() > 0
                    ? String.format(java.util.Locale.ROOT, "EX %.1fs", state.cooldownRemaining()) : "EX ready",
                    (float) state.x() - 21, (float) state.y() - 25);
        }
        g.setStroke(new BasicStroke(2));
        for (Volley volley : volleys.values()) {
            double progress = Math.min(1, (now - volley.started()) / (double) (volley.until() - volley.started()));
            for (ShotEndpoint endpoint : volley.endpoints()) {
                g.setColor(new Color(250, 120, 95, 150));
                g.draw(new Line2D.Double(volley.x(), volley.y(), endpoint.x(), endpoint.y()));
                double x = volley.x() + (endpoint.x() - volley.x()) * progress;
                double y = volley.y() + (endpoint.y() - volley.y()) * progress;
                ExaminatorArt.draw(g, assets, "EXAMINATOR_EXAM_PAPER", new Rectangle2D.Double(x - 8, y - 8, 16, 16));
                ExaminatorArt.draw(g, assets, "EXAMINATOR_IMPACT",
                        new Rectangle2D.Double(endpoint.x() - 10, endpoint.y() - 10, 20, 20));
            }
        }
    }

    private record Volley(double x, double y, List<ShotEndpoint> endpoints, long started, long until) { }
}
