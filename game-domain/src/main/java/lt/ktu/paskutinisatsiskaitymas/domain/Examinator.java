package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Retained per-character ability; all mutation and complete iterator consumption happen on the owner thread. */
public final class Examinator {
    public static final double WINDUP_SECONDS = 0.5;
    public static final double COOLDOWN_SECONDS = 10;
    public static final double FIRING_SECONDS = 0.25;
    private static final double EPSILON = 0.000_000_001;
    private final GameCharacter attacker;
    private final List<Iterable<? extends AutoTarget>> targetSources;
    private final List<AttackEffect> effects;
    private final Set<AutoTarget> struck = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Position> endpoints = new ArrayList<>();
    private double windupRemaining;
    private double cooldownRemaining;
    private double firingRemaining;
    private long activationRespawn;
    private long activationProne;
    private long volleySequence;
    private Position shotOrigin;

    public Examinator(GameCharacter attacker, List<Iterable<? extends AutoTarget>> targetSources) {
        this.attacker = Objects.requireNonNull(attacker, "attacker");
        this.targetSources = List.copyOf(targetSources);
        effects = attacker.characterType().loadout().effectsFor(AttackType.RANGED);
        shotOrigin = position();
    }

    public boolean activate() {
        if (attacker.isProne() || cooldownRemaining > EPSILON) {
            return false;
        }
        cooldownRemaining = COOLDOWN_SECONDS;
        windupRemaining = WINDUP_SECONDS;
        activationRespawn = attacker.respawnSequence();
        activationProne = attacker.proneSequence();
        return true;
    }

    public void update(double seconds) {
        NPCGeometry.positive(seconds, "seconds");
        cooldownRemaining = remaining(cooldownRemaining, seconds);
        firingRemaining = remaining(firingRemaining, seconds);
        if (firingRemaining == 0) {
            endpoints.clear();
        }
        if (windupRemaining > 0) {
            if (attacker.isProne() || attacker.respawnSequence() != activationRespawn
                    || attacker.proneSequence() != activationProne) {
                windupRemaining = 0;
                return;
            }
            windupRemaining = remaining(windupRemaining, seconds);
            if (windupRemaining == 0) {
                fire();
            }
        }
    }

    private void fire() {
        volleySequence++;
        shotOrigin = position();
        endpoints.clear();
        struck.clear();
        for (Iterable<? extends AutoTarget> source : targetSources) {
            fireAtAll(source.iterator());
        }
        struck.clear();
        firingRemaining = FIRING_SECONDS;
    }

    private void fireAtAll(Iterator<? extends AutoTarget> iterator) {
        while (iterator.hasNext()) {
            AutoTarget target = iterator.next();
            if (target.canBeTargetedBy(attacker)) {
                fireAt(target);
            }
        }
    }

    private void fireAt(AutoTarget target) {
        if (!struck.add(target)) {
            return;
        }
        Position endpoint = target.aimPoint();
        endpoints.add(endpoint);
        target.receiveHit(effects, endpoint.x() >= shotOrigin.x() ? 1 : -1);
    }

    private Position position() { return new Position(attacker.aimPoint().x(), attacker.y() - 28); }

    public ExaminatorState state() {
        String phase = windupRemaining > 0 ? "SCAN" : firingRemaining > 0 ? "FIRE" : "IDLE";
        return new ExaminatorState(attacker.playerId(), position(), phase, cooldownRemaining,
                windupRemaining > 0 ? windupRemaining : firingRemaining, volleySequence, shotOrigin, endpoints);
    }

    private static double remaining(double value, double seconds) {
        double next = Math.max(0, value - seconds);
        return next < EPSILON ? 0 : next;
    }
}
