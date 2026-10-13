package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Image;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import lt.ktu.paskutinisatsiskaitymas.protocol.NPCSnapshot;

/** EDT-confined image selection only. The server supplies activity, facing, geometry and attack sequence. */
final class NPCPresentation {
    static final long ATTACK_POSE_NANOS = 180_000_000L;
    // Single replacement/style location. Contact fractions align opaque feet/hands, retaining PNG aspect ratio.
    private static final Map<String, PoseStyle> STYLES = Map.of(
            "PATROL", new PoseStyle("NPC_PATROL", 1.00, 1927.0 / 1930),
            "CHASE", new PoseStyle("NPC_CHASE", 0.94, 1512.0 / 1536),
            "ATTACK", new PoseStyle("NPC_ATTACK", 0.80, 1320.0 / 1330),
            "FLEE", new PoseStyle("NPC_FLEE", 0.50, 960.0 / 1024));
    private final Map<Integer, AttackFeedback> feedback = new HashMap<>();
    private final LongSupplier clock;

    NPCPresentation() { this(System::nanoTime); }

    NPCPresentation(LongSupplier clock) { this.clock = clock; }

    void update(List<NPCSnapshot> npcs) {
        long now = clock.getAsLong();
        feedback.keySet().removeIf(id -> npcs.stream().noneMatch(npc -> npc.id() == id));
        for (NPCSnapshot npc : npcs) {
            AttackFeedback previous = feedback.get(npc.id());
            // First observation initializes the sequence without replaying historical attacks.
            long until = previous == null ? 0 : previous.until();
            if (previous != null && npc.attackAttemptSequence() > previous.sequence()) {
                until = now + ATTACK_POSE_NANOS;
            }
            feedback.put(npc.id(), new AttackFeedback(npc.attackAttemptSequence(), until));
        }
    }

    PoseStyle style(NPCSnapshot npc) {
        AttackFeedback value = feedback.get(npc.id());
        boolean attack = value != null && value.until() != 0 && clock.getAsLong() - value.until() < 0;
        return STYLES.get(attack ? "ATTACK" : npc.activity());
    }

    boolean hasActiveLatch() {
        long now = clock.getAsLong();
        return feedback.values().stream().anyMatch(value -> value.until() != 0 && now - value.until() < 0);
    }

    void clear() { feedback.clear(); }

    static PoseStyle patrolStyle() { return STYLES.get("PATROL"); }

    record PoseStyle(String assetKey, double heightFactor, double contactFraction) {
        Rectangle2D bounds(NPCSnapshot npc, Image image, double scale, double offsetX, double offsetY) {
            double height = npc.height() * heightFactor * scale;
            double width = height * image.getWidth(null) / image.getHeight(null);
            double centerX = offsetX + (npc.x() + npc.width() / 2) * scale;
            double groundY = offsetY + (npc.y() + npc.height()) * scale;
            return new Rectangle2D.Double(centerX - width / 2, groundY - height * contactFraction, width, height);
        }
    }

    private record AttackFeedback(long sequence, long until) { }
}
