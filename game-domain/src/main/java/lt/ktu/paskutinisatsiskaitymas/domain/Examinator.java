package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Retained ability, advanced only by the session owner. No iterator survives a method call. */
public final class Examinator {
    public static final double COOLDOWN_SECONDS = 30;
    public static final double FLIGHT_SECONDS = 0.8;
    public static final double OFFSCREEN_MARGIN = 64;
    public static final double INSPECTION_SECONDS = 0.6;
    public static final double ACQUIRE_SECONDS = 0.2;
    public static final double AIM_SECONDS = 0.5;
    public static final double FIRING_SECONDS = 0.2;
    /** Bounds continuously arriving targets; ordinary four-player/eight-projectile arenas finish well before this. */
    public static final double MAX_ACTIVE_SECONDS = 120;
    private static final double EPSILON = 0.000_000_001;
    private final GameCharacter attacker;
    private final List<Iterable<? extends AutoTarget>> targetSources;
    private final List<AttackEffect> effects;
    private final Set<AutoTarget> considered = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<ExaminatorShot> shots = new ArrayList<>();
    private final Position destination;
    private Phase phase = Phase.IDLE;
    private double phaseRemaining;
    private double cooldownRemaining;
    private double activeSeconds;
    private double simulationTime;
    private long activationRespawn;
    private long activationProne;
    private long shotSequence;
    private int sourceIndex;
    private AutoTarget locked;
    private long lockedLifetime;
    private final Position launch;
    private Position drone;
    private Position aim;

    public Examinator(GameCharacter attacker, List<Iterable<? extends AutoTarget>> targetSources, Arena arena) {
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.targetSources = List.copyOf(targetSources);
        destination = new Position(arena.width() / 2, arena.height() / 2);
        effects = attacker.characterType().loadout().effectsFor(AttackType.RANGED);
        launch = new Position(arena.width() / 2, -OFFSCREEN_MARGIN);
        drone = launch;
        aim = emitter();
    }

    public boolean activate() {
        if (phase != Phase.IDLE || attacker.isProne() || cooldownRemaining > EPSILON) {
            return false;
        }
        cooldownRemaining = COOLDOWN_SECONDS;
        activationRespawn = attacker.respawnSequence();
        activationProne = attacker.proneSequence();
        drone = launch;
        aim = emitter();
        sourceIndex = 0;
        activeSeconds = 0;
        considered.clear();
        shots.clear();
        enter(Phase.FLY, FLIGHT_SECONDS);
        return true;
    }

    public void update(double seconds) {
        NPCGeometry.positive(seconds, "seconds");
        cooldownRemaining = remaining(cooldownRemaining, seconds);
        if (attacker.isProne() || attacker.respawnSequence() != activationRespawn
                || attacker.proneSequence() != activationProne) {
            finish();
        }
        double budget = seconds;
        while (budget > EPSILON && phase != Phase.IDLE) {
            if (locked != null && !validLock()) {
                locked = null;
                enter(Phase.ACQUIRE, ACQUIRE_SECONDS);
            }
            if (locked != null) {
                aim = locked.aimPoint();
            }
            double step = Math.min(budget, Math.min(phaseRemaining, MAX_ACTIVE_SECONDS - activeSeconds));
            ageFeedback(step);
            budget = remaining(budget, step);
            activeSeconds += step;
            phaseRemaining = remaining(phaseRemaining, step);
            if (phase == Phase.FLY) {
                double fraction = 1 - phaseRemaining / FLIGHT_SECONDS;
                drone = new Position(launch.x() + (destination.x() - launch.x()) * fraction,
                        launch.y() + (destination.y() - launch.y()) * fraction);
            }
            if (activeSeconds >= MAX_ACTIVE_SECONDS - EPSILON) {
                finish();
            } else if (phaseRemaining == 0) {
                nextPhase();
            }
        }
        ageFeedback(budget);
        simulationTime += seconds;
    }

    private void nextPhase() {
        switch (phase) {
            case FLY -> enter(Phase.INSPECT, INSPECTION_SECONDS);
            case INSPECT, FIRE -> {
                locked = null;
                enter(Phase.ACQUIRE, ACQUIRE_SECONDS);
            }
            case ACQUIRE -> {
                locked = acquire();
                if (locked == null) {
                    finish();
                } else {
                    lockedLifetime = locked.targetLifetime();
                    aim = locked.aimPoint();
                    enter(Phase.AIM, AIM_SECONDS);
                }
            }
            case AIM -> {
                // Membership traversal has returned before any effect can change a source.
                if (validLock()) {
                    aim = locked.aimPoint();
                    Position origin = emitter();
                    shots.add(new ExaminatorShot(++shotSequence, origin, aim, FIRING_SECONDS));
                    locked.receiveHit(effects, aim.x() >= origin.x() ? 1 : -1);
                    locked = null;
                    enter(Phase.FIRE, FIRING_SECONDS);
                } else {
                    locked = null;
                    enter(Phase.ACQUIRE, ACQUIRE_SECONDS);
                }
            }
            case IDLE -> { }
        }
    }

    /** Common selection for all three aggregates; exhausted sources are never revisited this activation. */
    private AutoTarget acquire() {
        while (sourceIndex < targetSources.size()) {
            Iterator<? extends AutoTarget> iterator = targetSources.get(sourceIndex).iterator();
            while (iterator.hasNext()) {
                AutoTarget target = iterator.next();
                if (considered.add(target) && target.canBeTargetedBy(attacker)) {
                    return target;
                }
            }
            sourceIndex++;
        }
        return null;
    }

    private boolean validLock() {
        if (locked.targetLifetime() != lockedLifetime || !locked.canBeTargetedBy(attacker)) {
            return false;
        }
        Iterator<? extends AutoTarget> iterator = targetSources.get(sourceIndex).iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == locked) {
                return true;
            }
        }
        return false;
    }

    private void ageFeedback(double seconds) {
        shots.replaceAll(shot -> new ExaminatorShot(shot.sequence(), shot.origin(), shot.endpoint(),
                remaining(shot.remainingSeconds(), seconds)));
        shots.removeIf(shot -> shot.remainingSeconds() == 0);
    }

    private void enter(Phase next, double duration) { phase = next; phaseRemaining = duration; }

    private void finish() {
        enter(Phase.IDLE, 0);
        locked = null;
        considered.clear();
        shots.clear();
    }

    private Position emitter() { return new Position(drone.x() + 7, drone.y() - 3); }

    public ExaminatorState state() {
        return new ExaminatorState(attacker.playerId(), drone, phase.name(), cooldownRemaining,
                phaseRemaining, simulationTime, shotSequence, emitter(), aim, shots);
    }

    private static double remaining(double value, double seconds) {
        double next = Math.max(0, value - seconds);
        return next < EPSILON ? 0 : next;
    }

    private enum Phase { IDLE, FLY, INSPECT, ACQUIRE, AIM, FIRE }
}
