package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Selects algorithms, not motion. Timers use elapsed simulation seconds, never wall-clock time. */
public final class NPCBehaviorController {
    private NPCActivity activity = NPCActivity.PATROL;
    private Integer targetId;
    private final double detectionRadius;
    private final double detectionSeconds;
    private double timeDistance;
    private final double safeDistance;
    private final double maxChaseSeconds;
    private final double maxFleeSeconds;
    // STRATEGY-REPORT R4 BEGIN: Each controller owns a complete per-NPC strategy set.
    private final Map<NPCActivity, NPCActivityStrategy> strategies;
    // STRATEGY-REPORT R4 END
    private double detectionElapsed;
    private final NPC npc;

    public NPCBehaviorController(NPC npc, PatrolStrategy patrol, double detectionRadius,
            double detectionSeconds, double safeDistance, double maxChaseSeconds, double maxFleeSeconds) {
        this.npc = Objects.requireNonNull(npc, "npc");
        this.detectionRadius = NPCGeometry.positive(detectionRadius, "detectionRadius");
        this.detectionSeconds = NPCGeometry.positive(detectionSeconds, "detectionSeconds");
        this.safeDistance = NPCGeometry.positive(safeDistance, "safeDistance");
        this.maxChaseSeconds = NPCGeometry.positive(maxChaseSeconds, "maxChaseSeconds");
        this.maxFleeSeconds = NPCGeometry.positive(maxFleeSeconds, "maxFleeSeconds");
        Map<NPCActivity, NPCActivityStrategy> configured = new EnumMap<>(NPCActivity.class);
        configured.put(NPCActivity.PATROL, Objects.requireNonNull(patrol, "patrol"));
        configured.put(NPCActivity.CHASE, new ChaseStrategy(GameConstants.NPC_CHASE_MULTIPLIER));
        configured.put(NPCActivity.ATTACK, new AttackStrategy());
        configured.put(NPCActivity.FLEE, new FleeStrategy(GameConstants.NPC_FLEE_MULTIPLIER));
        strategies = Map.copyOf(configured);
        switchActivity(NPCActivity.PATROL);
    }

    public void update(List<NPCTarget> targets, double seconds) {
        Objects.requireNonNull(targets, "targets");
        NPCGeometry.positive(seconds, "seconds");
        timeDistance += seconds;
        NPCTarget target = targets.stream().filter(value -> value.id().equals(targetId)).findFirst().orElse(null);
        if (activity != NPCActivity.PATROL && target == null) {
            switchActivity(NPCActivity.PATROL);
            return;
        }
        switch (activity) {
            case PATROL -> detect(targets, seconds);
            case CHASE -> {
                if (timeDistance + 0.000_000_001 >= maxChaseSeconds) {
                    switchActivity(NPCActivity.PATROL);
                } else if (NPCGeometry.distance(npc.state(), target) <= npc.state().attackRange()) {
                    switchActivity(NPCActivity.ATTACK);
                }
            }
            case ATTACK -> {
                if (NPCGeometry.distance(npc.state(), target) > npc.state().attackRange()) {
                    switchActivity(NPCActivity.CHASE);
                }
            }
            case FLEE -> {
                if (timeDistance + 0.000_000_001 >= maxFleeSeconds || NPCGeometry.distance(npc.state(), target) >= safeDistance) {
                    switchActivity(NPCActivity.PATROL);
                }
            }
        }
        npc.faceTarget(activity == NPCActivity.ATTACK || activity == NPCActivity.FLEE, target);
    }

    private void detect(List<NPCTarget> targets, double seconds) {
        NPCState self = npc.state();
        NPCTarget nearest = targets.stream()
                .filter(value -> NPCGeometry.distance(self, value) <= detectionRadius)
                .min(Comparator.comparingDouble((NPCTarget value) -> NPCGeometry.distance(self, value))
                        .thenComparing(NPCTarget::id)).orElse(null);
        if (nearest == null) {
            targetId = null;
            detectionElapsed = 0;
        } else {
            if (!nearest.id().equals(targetId)) {
                detectionElapsed = 0;
            }
            targetId = nearest.id();
            detectionElapsed += seconds;
            if (detectionElapsed + 0.000_000_001 >= detectionSeconds) {
                switchActivity(NPCActivity.CHASE);
            }
        }
        npc.setTargetId(targetId);
    }

    /** True means player state changed; a shield block or rejection keeps ATTACK active. */
    public void onAttackResolved(boolean damaged) {
        if (damaged && activity == NPCActivity.ATTACK) {
            switchActivity(NPCActivity.FLEE);
        }
    }

    // STRATEGY-REPORT R4 BEGIN: Selection installs the algorithm through the context interface setter.
    public void switchActivity(NPCActivity next) {
        activity = Objects.requireNonNull(next, "next");
        npc.setActivityStrategy(strategies.get(next));
        timeDistance = 0;
        if (next == NPCActivity.PATROL) {
            targetId = null;
            detectionElapsed = 0;
        }
        npc.setTargetId(targetId);
        npc.faceTarget(next == NPCActivity.ATTACK || next == NPCActivity.FLEE, null);
    }
    // STRATEGY-REPORT R4 END

    public NPCActivity activity() { return activity; }

    void clearTarget(Integer removedId) {
        if (Objects.equals(targetId, removedId)) {
            switchActivity(NPCActivity.PATROL);
        }
    }
}
