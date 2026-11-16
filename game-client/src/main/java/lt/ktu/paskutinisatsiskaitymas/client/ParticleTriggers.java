package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.protocol.AttackSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.ItemSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.NPCSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlatformSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlayerSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.ProjectileSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/**
 * Derives particle bursts from the difference between two consecutive authoritative snapshots. The server sends
 * snapshots at 20 Hz, so a projectile can travel tens of units, or live and die entirely, between two of them;
 * triggers therefore key on durable state changes (health, items, flags) rather than on short-lived objects.
 */
final class ParticleTriggers {
    private static final long DUST_INTERVAL_NANOS = 70_000_000L;
    private static final long SHIMMER_INTERVAL_NANOS = 400_000_000L;
    private static final long PLATFORM_DUST_INTERVAL_NANOS = 120_000_000L;
    /**
     * Client-side estimate of how long a speed or jump boost lasts after pickup. Snapshots do not carry boost state
     * and this module may not import the domain, so this mirrors the server's item effect duration.
     */
    private static final long BOOST_NANOS = 8_000_000_000L;
    private static final String COLLECTED_INFIX = " picked up ";
    private static final double DUST_MIN_SPEED = 20.0;
    private static final double BLOCK_MARGIN = 40.0;
    private static final double NPC_RANGED_MARGIN_X = 45.0;
    private static final double NPC_RANGED_MARGIN_Y = 12.0;
    /** The server collects an item within 16 units of a player; allow for a player moving between snapshots. */
    private static final double PICKUP_REACH = 45.0;
    private static final double MIN_LANDING_FALL = 20.0;
    private static final double PLATFORM_MIN_MOVE = 0.05;
    private static final double ORBIT_RADIANS_PER_SECOND = 5.0;

    private final ParticleSystem particles;
    private final Map<UUID, Long> lastDust = new HashMap<>();
    private final Map<UUID, Long> speedBoostUntil = new HashMap<>();
    private final Map<UUID, Long> jumpBoostUntil = new HashMap<>();
    private final Map<UUID, Double> airborneTop = new HashMap<>();
    private final Map<UUID, Long> lastShimmer = new HashMap<>();
    private final Map<Integer, Long> lastPlatformDust = new HashMap<>();
    private final Map<UUID, Set<Integer>> meleeNpcHits = new HashMap<>();

    ParticleTriggers(ParticleSystem particles) {
        this.particles = particles;
    }

    void process(WorldSnapshot previous, WorldSnapshot current, long nowNanos) {
        if (previous == null) {
            reset();
            return;
        }
        emitMuzzle(previous, current);
        emitWoundsAndDeaths(previous, current);
        emitShieldBlocks(previous, current);
        emitNpcHits(previous, current);
        emitPickups(previous, current, nowNanos);
        emitJumpsAndLandings(previous, current, nowNanos);
        emitDust(current, nowNanos);
        emitAuras(current, nowNanos);
        emitItemSparkles(previous, current, nowNanos);
        emitPlatformDust(previous, current, nowNanos);
    }

    /**
     * Registers a speed or jump boost from the server's "nickname picked up TYPE" notification, which is exact where
     * guessing the collector from positions can be wrong when players stand close together.
     */
    void itemCollected(String text, WorldSnapshot latest, long nowNanos) {
        int split = text.lastIndexOf(COLLECTED_INFIX);
        if (latest == null || split < 0) {
            return;
        }
        String nickname = text.substring(0, split);
        String itemType = text.substring(split + COLLECTED_INFIX.length());
        for (PlayerSnapshot player : latest.players()) {
            if (player.nickname().equals(nickname)) {
                registerBoost(itemType, player.playerId(), nowNanos);
            }
        }
    }

    void reset() {
        lastDust.clear();
        speedBoostUntil.clear();
        jumpBoostUntil.clear();
        airborneTop.clear();
        lastShimmer.clear();
        lastPlatformDust.clear();
        meleeNpcHits.clear();
    }

    private void emitMuzzle(WorldSnapshot previous, WorldSnapshot current) {
        Set<UUID> before = new HashSet<>();
        previous.attacks().forEach(attack -> before.add(attack.id()));
        for (AttackSnapshot attack : current.attacks()) {
            if (isRanged(attack) && !before.contains(attack.id())) {
                particles.emitBurst(ParticleKind.MUZZLE, centerX(attack), centerY(attack), 8, attack.direction());
            }
        }
    }

    /**
     * Any health loss is a hit, whatever caused it (melee, shot, NPC projectile). Health only ever rises on
     * respawn, which also ends the server's timed effects, so that is shown as a bigger burst where the player fell.
     */
    private void emitWoundsAndDeaths(WorldSnapshot previous, WorldSnapshot current) {
        Map<UUID, PlayerSnapshot> before = byId(previous.players());
        for (PlayerSnapshot player : current.players()) {
            PlayerSnapshot old = before.get(player.playerId());
            if (old == null) {
                continue;
            }
            if (player.health() < old.health()) {
                particles.emitBurst(ParticleKind.HIT, player.x() + player.width() / 2,
                        player.y() + player.height() / 2, 14, 1);
            } else if (player.health() > old.health()) {
                particles.emitBurst(ParticleKind.HIT, old.x() + old.width() / 2,
                        old.y() + old.height() / 2, 24, 1);
                speedBoostUntil.remove(player.playerId());
                jumpBoostUntil.remove(player.playerId());
            }
        }
    }

    /** A shot that vanished beside a shielded player was absorbed: health did not change, so show it explicitly. */
    private void emitShieldBlocks(WorldSnapshot previous, WorldSnapshot current) {
        Set<UUID> attacksAfter = new HashSet<>();
        current.attacks().forEach(attack -> attacksAfter.add(attack.id()));
        for (AttackSnapshot attack : previous.attacks()) {
            if (isRanged(attack) && !attacksAfter.contains(attack.id())
                    && shieldedPlayerNear(current, centerX(attack), centerY(attack), BLOCK_MARGIN, attack.sourceId())) {
                particles.emitBurst(ParticleKind.POWERUP_SHIELD, centerX(attack), centerY(attack), 8, 1);
            }
        }
        Set<UUID> projectilesAfter = new HashSet<>();
        current.projectiles().forEach(projectile -> projectilesAfter.add(projectile.id()));
        for (ProjectileSnapshot projectile : previous.projectiles()) {
            if (!projectilesAfter.contains(projectile.id())
                    && shieldedPlayerNear(current, projectile.x(), projectile.y(), BLOCK_MARGIN, null)) {
                particles.emitBurst(ParticleKind.POWERUP_SHIELD, projectile.x(), projectile.y(), 8, 1);
            }
        }
    }

    /**
     * NPC health is not on the wire, so NPC hits come from shots ending at an NPC, melee boxes and stuns; its
     * death comes from the {@code alive} flag.
     */
    private void emitNpcHits(WorldSnapshot previous, WorldSnapshot current) {
        Set<UUID> attacksAfter = new HashSet<>();
        current.attacks().forEach(attack -> attacksAfter.add(attack.id()));
        for (AttackSnapshot attack : previous.attacks()) {
            if (isRanged(attack) && !attacksAfter.contains(attack.id())) {
                for (NPCSnapshot npc : current.npcs()) {
                    if (npc.alive() && near(npc, centerX(attack), centerY(attack), NPC_RANGED_MARGIN_X,
                            NPC_RANGED_MARGIN_Y)) {
                        emitNpcImpact(npc, attack.direction());
                        break;
                    }
                }
            }
        }
        for (AttackSnapshot attack : current.attacks()) {
            if (isRanged(attack)) {
                continue;
            }
            Set<Integer> alreadyHit = meleeNpcHits.computeIfAbsent(attack.id(), id -> new HashSet<>());
            for (NPCSnapshot npc : current.npcs()) {
                if (npc.alive() && !alreadyHit.contains(npc.id()) && overlaps(attack, npc)) {
                    alreadyHit.add(npc.id());
                    emitNpcImpact(npc, attack.direction());
                }
            }
        }
        meleeNpcHits.keySet().retainAll(attacksAfter);
        Map<Integer, NPCSnapshot> npcsBefore = new HashMap<>();
        previous.npcs().forEach(npc -> npcsBefore.put(npc.id(), npc));
        for (NPCSnapshot npc : current.npcs()) {
            NPCSnapshot old = npcsBefore.get(npc.id());
            if (old == null) {
                continue;
            }
            if (old.alive() && !npc.alive()) {
                particles.emitBurst(ParticleKind.NPC_DEATH, npc.x() + npc.width() / 2,
                        npc.y() + npc.height() / 2, 24, 1);
            } else if (!old.stunned() && npc.stunned()) {
                emitNpcImpact(npc, 1);
            }
        }
    }

    private void emitNpcImpact(NPCSnapshot npc, int direction) {
        particles.emitBurst(ParticleKind.IMPACT, npc.x() + npc.width() / 2, npc.y() + npc.height() / 2, 8, direction);
    }

    /** Items never despawn, so one that vanished was collected; whoever stood closest gets any boost it carried. */
    private void emitPickups(WorldSnapshot previous, WorldSnapshot current, long nowNanos) {
        Set<UUID> after = new HashSet<>();
        current.items().forEach(item -> after.add(item.id()));
        for (ItemSnapshot item : previous.items()) {
            if (after.contains(item.id())) {
                continue;
            }
            ParticleKind kind = switch (item.type()) {
                case "SHIELD" -> ParticleKind.POWERUP_SHIELD;
                case "SPEED_BOOST" -> ParticleKind.POWERUP_SPEED;
                case "JUMP_BOOST" -> ParticleKind.POWERUP_JUMP;
                default -> null;
            };
            if (kind == null) {
                continue;
            }
            particles.emitBurst(kind, item.x(), item.y(), 20, 1);
            PlayerSnapshot collector = closestPlayer(previous, current, item.x(), item.y());
            if (collector != null) {
                registerBoost(item.type(), collector.playerId(), nowNanos);
            }
        }
    }

    private void registerBoost(String itemType, UUID playerId, long nowNanos) {
        if ("SPEED_BOOST".equals(itemType)) {
            speedBoostUntil.put(playerId, nowNanos + BOOST_NANOS);
        } else if ("JUMP_BOOST".equals(itemType)) {
            jumpBoostUntil.put(playerId, nowNanos + BOOST_NANOS);
        }
    }

    /** Takeoff puff, and on landing a dust ring sized by how far the player fell from the top of the arc. */
    private void emitJumpsAndLandings(WorldSnapshot previous, WorldSnapshot current, long nowNanos) {
        Map<UUID, PlayerSnapshot> before = byId(previous.players());
        Set<UUID> present = new HashSet<>();
        for (PlayerSnapshot player : current.players()) {
            UUID id = player.playerId();
            present.add(id);
            PlayerSnapshot old = before.get(id);
            if (old == null) {
                continue;
            }
            double feetX = player.x() + player.width() / 2;
            double feetY = player.y() + player.height();
            if (old.grounded() && !player.grounded()) {
                airborneTop.put(id, player.y());
                if (player.y() < old.y() - 0.01) {
                    emitDustBothSides(feetX, feetY, 4);
                    if (boostActive(jumpBoostUntil, id, nowNanos)) {
                        particles.emitBurst(ParticleKind.JUMP_TRAIL, feetX, feetY, 10, 1);
                    }
                }
            } else if (!player.grounded()) {
                airborneTop.merge(id, player.y(), Math::min);
            } else if (!old.grounded()) {
                double fall = player.y() - airborneTop.getOrDefault(id, player.y());
                if (fall >= MIN_LANDING_FALL) {
                    emitDustBothSides(feetX, feetY, (int) Math.max(3, Math.min(12, 2 + fall / 25)));
                    if (boostActive(jumpBoostUntil, id, nowNanos)) {
                        particles.emitBurst(ParticleKind.JUMP_TRAIL, feetX, feetY, 8, 1);
                    }
                }
            }
            if (player.grounded()) {
                airborneTop.remove(id);
            }
        }
        airborneTop.keySet().retainAll(present);
    }

    private void emitDustBothSides(double x, double y, int countPerSide) {
        particles.emitBurst(ParticleKind.DUST, x, y, countPerSide, -1);
        particles.emitBurst(ParticleKind.DUST, x, y, countPerSide, 1);
    }

    private void emitDust(WorldSnapshot current, long nowNanos) {
        Set<UUID> present = new HashSet<>();
        for (PlayerSnapshot player : current.players()) {
            present.add(player.playerId());
            if (!player.grounded() || Math.abs(player.velocityX()) <= DUST_MIN_SPEED) {
                continue;
            }
            Long last = lastDust.get(player.playerId());
            if (last == null || nowNanos - last >= DUST_INTERVAL_NANOS) {
                lastDust.put(player.playerId(), nowNanos);
                double feetX = player.x() + player.width() / 2;
                double feetY = player.y() + player.height();
                int behind = player.velocityX() > 0 ? -1 : 1;
                particles.emitBurst(ParticleKind.DUST, feetX, feetY, 2, behind);
                if (boostActive(speedBoostUntil, player.playerId(), nowNanos)) {
                    particles.emitBurst(ParticleKind.SPEED_TRAIL, feetX, feetY, 2, behind);
                }
            }
        }
        lastDust.keySet().retainAll(present);
        speedBoostUntil.keySet().retainAll(present);
        jumpBoostUntil.keySet().retainAll(present);
    }

    /** A shielded player keeps two motes orbiting them for as long as the shield lasts. */
    private void emitAuras(WorldSnapshot current, long nowNanos) {
        double phase = nowNanos / 1e9 * ORBIT_RADIANS_PER_SECOND;
        for (PlayerSnapshot player : current.players()) {
            if (player.shielded()) {
                emitShieldOrbit(player, phase);
            }
        }
    }

    private void emitShieldOrbit(PlayerSnapshot player, double phase) {
        double centerX = player.x() + player.width() / 2;
        double centerY = player.y() + player.height() / 2;
        for (int mote = 0; mote < 2; mote++) {
            double angle = phase + mote * Math.PI;
            particles.emitBurst(ParticleKind.SHIELD_AURA, centerX + Math.cos(angle) * (player.width() / 2 + 12.0),
                    centerY + Math.sin(angle) * (player.height() / 2 + 12.0), 1, 1);
        }
    }

    /** New items burst into sparkles; items already lying around twinkle now and then. */
    private void emitItemSparkles(WorldSnapshot previous, WorldSnapshot current, long nowNanos) {
        Set<UUID> before = new HashSet<>();
        previous.items().forEach(item -> before.add(item.id()));
        Set<UUID> present = new HashSet<>();
        for (ItemSnapshot item : current.items()) {
            present.add(item.id());
            if (!before.contains(item.id())) {
                particles.emitBurst(ParticleKind.SPARKLE, item.x(), item.y(), 12, 1, 6.0);
                lastShimmer.put(item.id(), nowNanos);
                continue;
            }
            Long last = lastShimmer.get(item.id());
            if (last == null) {
                lastShimmer.put(item.id(), nowNanos);
            } else if (nowNanos - last >= SHIMMER_INTERVAL_NANOS) {
                lastShimmer.put(item.id(), nowNanos);
                particles.emitBurst(ParticleKind.SPARKLE, item.x(), item.y(), 1, 1, 10.0);
            }
        }
        lastShimmer.keySet().retainAll(present);
    }

    /** Platforms that moved since the last snapshot shed grey dust from their trailing edge. */
    private void emitPlatformDust(WorldSnapshot previous, WorldSnapshot current, long nowNanos) {
        List<PlatformSnapshot> before = previous.arena().platforms();
        List<PlatformSnapshot> after = current.arena().platforms();
        if (before.size() != after.size()) {
            lastPlatformDust.clear();
            return;
        }
        for (int index = 0; index < after.size(); index++) {
            PlatformSnapshot old = before.get(index);
            PlatformSnapshot platform = after.get(index);
            double dx = platform.x() - old.x();
            double dy = platform.y() - old.y();
            if (Math.hypot(dx, dy) < PLATFORM_MIN_MOVE) {
                continue;
            }
            Long last = lastPlatformDust.get(index);
            if (last != null && nowNanos - last < PLATFORM_DUST_INTERVAL_NANOS) {
                continue;
            }
            lastPlatformDust.put(index, nowNanos);
            if (Math.abs(dx) >= Math.abs(dy)) {
                double edgeX = dx > 0 ? platform.x() : platform.x() + platform.width();
                particles.emitBurst(ParticleKind.PLATFORM_DUST, edgeX, platform.y(), 2, dx > 0 ? -1 : 1);
            } else {
                particles.emitBurst(ParticleKind.PLATFORM_DUST, platform.x(), platform.y(), 1, -1);
                particles.emitBurst(ParticleKind.PLATFORM_DUST, platform.x() + platform.width(), platform.y(), 1, 1);
            }
        }
    }

    /** The player nearest the point, judged by where they stood in either snapshot; null when nobody is close. */
    private static PlayerSnapshot closestPlayer(WorldSnapshot previous, WorldSnapshot current, double x, double y) {
        Map<UUID, PlayerSnapshot> before = byId(previous.players());
        PlayerSnapshot best = null;
        double bestDistance = PICKUP_REACH;
        for (PlayerSnapshot player : current.players()) {
            double distance = distanceToBox(player, x, y);
            PlayerSnapshot old = before.get(player.playerId());
            if (old != null) {
                distance = Math.min(distance, distanceToBox(old, x, y));
            }
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static double distanceToBox(PlayerSnapshot player, double x, double y) {
        double dx = Math.max(Math.max(player.x() - x, x - (player.x() + player.width())), 0);
        double dy = Math.max(Math.max(player.y() - y, y - (player.y() + player.height())), 0);
        return Math.hypot(dx, dy);
    }

    private static boolean shieldedPlayerNear(WorldSnapshot snapshot, double x, double y, double margin,
            UUID exceptId) {
        for (PlayerSnapshot player : snapshot.players()) {
            if (player.shielded() && !player.playerId().equals(exceptId) && x >= player.x() - margin
                    && x <= player.x() + player.width() + margin && y >= player.y() - margin
                    && y <= player.y() + player.height() + margin) {
                return true;
            }
        }
        return false;
    }

    private static Map<UUID, PlayerSnapshot> byId(List<PlayerSnapshot> players) {
        Map<UUID, PlayerSnapshot> result = new HashMap<>();
        players.forEach(player -> result.put(player.playerId(), player));
        return result;
    }

    private static boolean boostActive(Map<UUID, Long> until, UUID id, long nowNanos) {
        Long expiry = until.get(id);
        return expiry != null && nowNanos < expiry;
    }

    private static boolean near(NPCSnapshot npc, double x, double y, double marginX, double marginY) {
        return x >= npc.x() - marginX && x <= npc.x() + npc.width() + marginX
                && y >= npc.y() - marginY && y <= npc.y() + npc.height() + marginY;
    }

    private static boolean overlaps(AttackSnapshot attack, NPCSnapshot npc) {
        return attack.x() < npc.x() + npc.width() && attack.x() + attack.width() > npc.x()
                && attack.y() < npc.y() + npc.height() && attack.y() + attack.height() > npc.y();
    }

    private static boolean isRanged(AttackSnapshot attack) {
        return "RANGED".equals(attack.type());
    }

    private static double centerX(AttackSnapshot attack) {
        return attack.x() + attack.width() / 2;
    }

    private static double centerY(AttackSnapshot attack) {
        return attack.y() + attack.height() / 2;
    }
}
