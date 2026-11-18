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
import lt.ktu.paskutinisatsiskaitymas.protocol.ExaminatorShotSnapshot;

/** EDT-owned cosmetics. Stale simulation times are ignored; observed shot lifetimes never restart. */
final class ExaminatorPresentation {
    private final LongSupplier clock;
    private final ParticleSystem particles;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, Long> lastShot = new HashMap<>();
    private final Map<ShotKey, Beam> beams = new HashMap<>();
    private final Map<UUID, Double> thrusterRemainders = new HashMap<>();

    ExaminatorPresentation(ParticleSystem particles) { this(particles, System::nanoTime); }
    ExaminatorPresentation(LongSupplier clock) { this(null, clock); }
    ExaminatorPresentation(ParticleSystem particles, LongSupplier clock) {
        this.particles = particles;
        this.clock = clock;
    }

    void update(List<ExaminatorSnapshot> states) {
        long now = clock.getAsLong();
        for (ExaminatorSnapshot state : states) {
            View previous = views.get(state.ownerId());
            if (previous != null && state.simulationTime() <= previous.state().simulationTime()) {
                continue;
            }
            long until = now + (long) (state.phaseRemaining() * 1e9);
            if (previous != null && state.phase().equals("FIRE") && previous.state().phase().equals("FIRE")
                    && previous.state().shotSequence() == state.shotSequence()) {
                until = Math.min(until, previous.until());
            }
            views.put(state.ownerId(), new View(state, until));
            if (!state.phase().equals("FLY")) {
                thrusterRemainders.remove(state.ownerId());
            }
            if (!state.phase().equals("FIRE")) {
                beams.keySet().removeIf(key -> key.owner().equals(state.ownerId()));
            }
            if (particles != null && state.phase().equals("AIM")
                    && (previous == null || !previous.state().phase().equals("AIM"))) {
                particles.emitAngledBurst(ParticleKind.EXAMINATOR_CHARGE, state.emitterX(), state.emitterY(), 3,
                        Math.atan2(state.aimY() - state.emitterY(), state.aimX() - state.emitterX()), 1);
            }
            long previousShot = lastShot.getOrDefault(state.ownerId(), 0L);
            for (ExaminatorShotSnapshot shot : state.shots()) {
                if (shot.sequence() <= previousShot) {
                    continue;
                }
                beams.put(new ShotKey(state.ownerId(), shot.sequence()),
                        new Beam(shot, now + (long) (shot.remainingSeconds() * 1e9)));
                if (particles != null) {
                    double angle = Math.atan2(shot.endpointY() - shot.originY(), shot.endpointX() - shot.originX());
                    particles.emitAngledBurst(ParticleKind.EXAMINATOR_CHARGE, shot.originX(), shot.originY(), 4, angle, 1);
                    particles.emitAngledBurst(ParticleKind.EXAMINATOR_SPARK, shot.endpointX(), shot.endpointY(), 8,
                            angle + Math.PI, 2);
                }
            }
            lastShot.put(state.ownerId(), Math.max(previousShot, state.shotSequence()));
        }
        views.keySet().removeIf(id -> states.stream().noneMatch(state -> state.ownerId().equals(id)));
        lastShot.keySet().retainAll(views.keySet());
        thrusterRemainders.keySet().retainAll(views.keySet());
        beams.keySet().removeIf(key -> !views.containsKey(key.owner()));
        expire(now);
    }

    /** Continuous emission is driven by the existing presentation timer, never by painting. */
    void advance(double seconds) {
        if (particles == null) {
            return;
        }
        long now = clock.getAsLong();
        for (Map.Entry<UUID, View> entry : views.entrySet()) {
            View view = entry.getValue();
            if (!view.state().phase().equals("FLY") || now >= view.until()) {
                continue;
            }
            double count = thrusterRemainders.getOrDefault(entry.getKey(), 0.0) + seconds * 18;
            int whole = (int) count;
            thrusterRemainders.put(entry.getKey(), count - whole);
            particles.emitAngledBurst(ParticleKind.EXAMINATOR_THRUSTER, view.state().x(), view.state().y() + 18,
                    whole, Math.PI / 2, 5);
        }
    }

    boolean isActive() {
        long now = clock.getAsLong();
        expire(now);
        return !beams.isEmpty() || views.values().stream()
                .anyMatch(view -> !view.state().phase().equals("IDLE") && now < view.until());
    }

    int beamCount() { expire(clock.getAsLong()); return beams.size(); }
    void clear() { views.clear(); lastShot.clear(); beams.clear(); thrusterRemainders.clear(); }
    private void expire(long now) { beams.values().removeIf(beam -> now >= beam.until()); }

    void draw(Graphics2D g, AssetManager assets) {
        long now = clock.getAsLong();
        expire(now);
        for (View view : views.values()) {
            ExaminatorSnapshot state = view.state();
            String phase = now < view.until() ? state.phase() : "IDLE";
            String key = switch (phase) {
                case "FLY" -> "EXAMINATOR_ARRIVE";
                case "INSPECT" -> "EXAMINATOR_" + (state.phaseRemaining() > 0.3 ? "LEFT" : "RIGHT");
                case "ACQUIRE" -> "EXAMINATOR_HOVER";
                case "AIM" -> ExaminatorArt.cleanAimPose(state.aimX() - state.emitterX(),
                        state.aimY() - state.emitterY());
                case "FIRE" -> "EXAMINATOR_ATTACK_" + ExaminatorArt.direction(state.aimX() - state.emitterX(),
                        state.aimY() - state.emitterY());
                default -> "EXAMINATOR_IDLE";
            };
            ExaminatorArt.draw(g, assets, key, new Rectangle2D.Double(state.x() - 22, state.y() - 22, 44, 44));
            if (phase.equals("AIM")) {
                g.setColor(new Color(255, 65, 65, 100));
                g.setStroke(new BasicStroke(0.7f));
                g.draw(new Line2D.Double(state.emitterX(), state.emitterY(), state.aimX(), state.aimY()));
                g.draw(new Rectangle2D.Double(state.aimX() - 4, state.aimY() - 4, 8, 8));
            }
            g.setColor(Color.WHITE);
            g.drawString(state.cooldownRemaining() > 0
                    ? String.format(java.util.Locale.ROOT, "EX %.0fs", Math.ceil(state.cooldownRemaining())) : "EX ready",
                    (float) state.x() - 21, (float) state.y() - 25);
        }
        for (Beam beam : beams.values()) {
            ExaminatorShotSnapshot shot = beam.shot();
            ExaminatorArt.laser(g, assets, shot.originX(), shot.originY(), shot.endpointX(), shot.endpointY());
            g.setColor(new Color(255, 70, 70, 180));
            g.fill(new Rectangle2D.Double(shot.endpointX() - 5, shot.endpointY() - 2, 10, 4));
            g.setColor(Color.WHITE);
            g.fill(new Rectangle2D.Double(shot.endpointX() - 2, shot.endpointY() - 5, 4, 10));
        }
    }

    private record View(ExaminatorSnapshot state, long until) { }
    private record ShotKey(UUID owner, long sequence) { }
    private record Beam(ExaminatorShotSnapshot shot, long until) { }
}
