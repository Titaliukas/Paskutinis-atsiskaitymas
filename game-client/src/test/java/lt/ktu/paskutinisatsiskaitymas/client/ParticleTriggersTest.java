package lt.ktu.paskutinisatsiskaitymas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.protocol.ArenaSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.AttackSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.CreateLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.ItemSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.NPCSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlatformSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlayerSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.ProjectileSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import org.junit.jupiter.api.Test;

class ParticleTriggersTest {
    private static final long MS = 1_000_000L;
    private final UUID shooter = UUID.randomUUID();
    private final UUID victim = UUID.randomUUID();
    private final ParticleSystem particles = new ParticleSystem(new Random(1), 1000);
    private final ParticleTriggers triggers = new ParticleTriggers(particles);

    private PlayerSnapshot player(UUID id, int slot, double x, boolean grounded, double velocityX, int health) {
        return new PlayerSnapshot(id, slot, "P" + slot, x, 400, 42, 64, grounded, false, velocityX, health, 100, 0,
                CreateLobby.DEFAULT_CHARACTER, false);
    }

    private WorldSnapshot world(List<PlayerSnapshot> players, List<ItemSnapshot> items, List<NPCSnapshot> npcs,
            List<AttackSnapshot> attacks, List<ProjectileSnapshot> projectiles) {
        return new WorldSnapshot(1, new ArenaSnapshot("combat", 960, 540,
                List.of(new PlatformSnapshot(0, 480, 960, 60))), players, items, npcs, attacks, projectiles);
    }

    private WorldSnapshot players(PlayerSnapshot... players) {
        return world(List.of(players), List.of(), List.of(), List.of(), List.of());
    }

    private AttackSnapshot ranged(UUID id, double x) {
        return new AttackSnapshot(id, shooter, "RANGED", 1, x, 420, 10, 10);
    }

    @Test
    void newRangedAttackEmitsMuzzleBurst() {
        WorldSnapshot before = players(player(shooter, 0, 100, true, 0, 100));
        WorldSnapshot after = world(List.of(player(shooter, 0, 100, true, 0, 100)), List.of(), List.of(),
                List.of(ranged(UUID.randomUUID(), 150)), List.of());
        triggers.process(before, after, 0);
        assertEquals(8, particles.count(ParticleKind.MUZZLE));
    }

    @Test
    void meleeAttackEmitsNothing() {
        WorldSnapshot before = players(player(shooter, 0, 100, true, 0, 100));
        WorldSnapshot after = world(List.of(player(shooter, 0, 100, true, 0, 100)), List.of(), List.of(),
                List.of(new AttackSnapshot(UUID.randomUUID(), shooter, "MELEE", 1, 140, 400, 30, 40)), List.of());
        triggers.process(before, after, 0);
        assertEquals(0, particles.size());
    }

    @Test
    void rangedAttackThatWoundsAPlayerEmitsHitBurst() {
        UUID attackId = UUID.randomUUID();
        WorldSnapshot before = world(List.of(player(shooter, 0, 100, true, 0, 100),
                player(victim, 1, 300, true, 0, 100)), List.of(), List.of(), List.of(ranged(attackId, 290)),
                List.of());
        WorldSnapshot after = players(player(shooter, 0, 100, true, 0, 100), player(victim, 1, 300, true, 0, 90));
        triggers.process(before, after, 0);
        assertEquals(14, particles.count(ParticleKind.HIT));
    }

    @Test
    void rangedAttackExpiringInMidAirEmitsNothing() {
        UUID attackId = UUID.randomUUID();
        WorldSnapshot before = world(List.of(player(shooter, 0, 100, true, 0, 100)), List.of(), List.of(),
                List.of(ranged(attackId, 600)), List.of());
        WorldSnapshot after = players(player(shooter, 0, 100, true, 0, 100));
        triggers.process(before, after, 0);
        assertEquals(0, particles.size());
    }

    @Test
    void rangedAttackReachingAnNpcEmitsImpact() {
        UUID attackId = UUID.randomUUID();
        NPCSnapshot npc = new NPCSnapshot(1, 500, 416, 42, 64, "PATROL", true, 0, false);
        WorldSnapshot before = world(List.of(player(shooter, 0, 100, true, 0, 100)), List.of(), List.of(npc),
                List.of(ranged(attackId, 495)), List.of());
        WorldSnapshot after = world(List.of(player(shooter, 0, 100, true, 0, 100)), List.of(), List.of(npc),
                List.of(), List.of());
        triggers.process(before, after, 0);
        assertEquals(8, particles.count(ParticleKind.IMPACT));
    }

    @Test
    void npcProjectileThatWoundsAPlayerEmitsHitBurst() {
        ProjectileSnapshot book = new ProjectileSnapshot(UUID.randomUUID(), 1, "BOOK", 310, 430, 10, -200, 0, false);
        WorldSnapshot before = world(List.of(player(victim, 1, 300, true, 0, 100)), List.of(), List.of(),
                List.of(), List.of(book));
        WorldSnapshot after = players(player(victim, 1, 300, true, 0, 95));
        triggers.process(before, after, 0);
        assertEquals(14, particles.count(ParticleKind.HIT));
    }

    @Test
    void collectedItemEmitsPowerupColourForItsType() {
        ItemSnapshot shield = new ItemSnapshot(UUID.randomUUID(), "SHIELD", 120, 430);
        ItemSnapshot speed = new ItemSnapshot(UUID.randomUUID(), "SPEED_BOOST", 120, 430);
        ItemSnapshot jump = new ItemSnapshot(UUID.randomUUID(), "JUMP_BOOST", 120, 430);
        WorldSnapshot before = world(List.of(player(shooter, 0, 100, true, 0, 100)), List.of(shield, speed, jump),
                List.of(), List.of(), List.of());
        WorldSnapshot after = players(player(shooter, 0, 100, true, 0, 100));
        triggers.process(before, after, 0);
        assertEquals(20, particles.count(ParticleKind.POWERUP_SHIELD));
        assertEquals(20, particles.count(ParticleKind.POWERUP_SPEED));
        assertEquals(20, particles.count(ParticleKind.POWERUP_JUMP));
    }

    @Test
    void collectedItemAlwaysBurstsEvenWhenNoPlayerIsInReachButGrantsNoBoost() {
        ItemSnapshot speed = new ItemSnapshot(UUID.randomUUID(), "SPEED_BOOST", 800, 430);
        WorldSnapshot before = world(List.of(player(shooter, 0, 100, true, 0, 100)), List.of(speed), List.of(),
                List.of(), List.of());
        WorldSnapshot after = players(player(shooter, 0, 100, true, 0, 100));
        triggers.process(before, after, 0);
        assertEquals(20, particles.count(ParticleKind.POWERUP_SPEED));
        WorldSnapshot running = players(player(shooter, 0, 100, true, 200, 100));
        triggers.process(running, running, 100 * MS);
        assertEquals(0, particles.count(ParticleKind.SPEED_TRAIL));
    }

    @Test
    void pickupJustOutsideThePlayerBoxStillCountsAsCollected() {
        ItemSnapshot boost = new ItemSnapshot(UUID.randomUUID(), "SPEED_BOOST", 160, 430);
        WorldSnapshot before = world(List.of(standingAt(shooter, 400)), List.of(boost), List.of(), List.of(), List.of());
        WorldSnapshot after = players(standingAt(shooter, 400));
        triggers.process(before, after, 0);
        particles.clear();
        WorldSnapshot running = players(player(shooter, 0, 100, true, 200, 100));
        triggers.process(running, running, 100 * MS);
        assertEquals(2, particles.count(ParticleKind.SPEED_TRAIL));
    }

    @Test
    void groundedRunningPlayerKicksUpRateLimitedDust() {
        WorldSnapshot snapshot = players(player(shooter, 0, 100, true, 200, 100));
        triggers.process(snapshot, snapshot, 0);
        assertEquals(2, particles.count(ParticleKind.DUST));
        triggers.process(snapshot, snapshot, 30 * MS);
        assertEquals(2, particles.count(ParticleKind.DUST));
        triggers.process(snapshot, snapshot, 80 * MS);
        assertEquals(4, particles.count(ParticleKind.DUST));
    }

    @Test
    void standingOrAirbornePlayerRaisesNoDust() {
        WorldSnapshot standing = players(player(shooter, 0, 100, true, 0, 100));
        WorldSnapshot airborne = players(player(shooter, 0, 100, false, 200, 100));
        triggers.process(standing, standing, 0);
        triggers.process(airborne, airborne, 100 * MS);
        assertEquals(0, particles.size());
    }

    @Test
    void firstSnapshotEmitsNothing() {
        triggers.process(null, players(player(shooter, 0, 100, true, 200, 100)), 0);
        assertEquals(0, particles.size());
    }

    private PlayerSnapshot airborne(UUID id, double y) {
        return new PlayerSnapshot(id, 0, "P0", 100, y, 42, 64, false, false, 0, 100, 100, 0,
                CreateLobby.DEFAULT_CHARACTER, false);
    }

    private PlayerSnapshot standingAt(UUID id, double y) {
        return new PlayerSnapshot(id, 0, "P0", 100, y, 42, 64, true, false, 0, 100, 100, 0,
                CreateLobby.DEFAULT_CHARACTER, false);
    }

    private WorldSnapshot withItems(PlayerSnapshot player, ItemSnapshot... items) {
        return world(List.of(player), List.of(items), List.of(), List.of(), List.of());
    }

    private WorldSnapshot withPlatform(double x, double y) {
        return new WorldSnapshot(1, new ArenaSnapshot("combat", 960, 540,
                List.of(new PlatformSnapshot(x, y, 100, 20))),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void jumpingEmitsPuffAtTheFeetButWalkingOffALedgeDoesNot() {
        triggers.process(players(standingAt(shooter, 400)), players(airborne(shooter, 380)), 0);
        assertEquals(8, particles.count(ParticleKind.DUST));
        particles.clear();
        triggers.reset();
        triggers.process(players(standingAt(shooter, 400)), players(airborne(shooter, 410)), 0);
        assertEquals(0, particles.size());
    }

    @Test
    void landingDustScalesWithFallHeightAndIgnoresSmallDrops() {
        triggers.process(players(standingAt(shooter, 400)), players(airborne(shooter, 300)), 0);
        triggers.process(players(airborne(shooter, 300)), players(airborne(shooter, 350)), MS);
        particles.clear();
        triggers.process(players(airborne(shooter, 350)), players(standingAt(shooter, 400)), 2 * MS);
        assertEquals(12, particles.count(ParticleKind.DUST));

        particles.clear();
        triggers.process(players(standingAt(shooter, 400)), players(airborne(shooter, 395)), 3 * MS);
        particles.clear();
        triggers.process(players(airborne(shooter, 395)), players(standingAt(shooter, 400)), 4 * MS);
        assertEquals(0, particles.size());
    }

    @Test
    void jumpBoostAddsGreenBurstsOnJumpAndLandingUntilItExpires() {
        ItemSnapshot boost = new ItemSnapshot(UUID.randomUUID(), "JUMP_BOOST", 120, 430);
        triggers.process(withItems(standingAt(shooter, 400), boost), players(standingAt(shooter, 400)), 0);
        particles.clear();
        triggers.process(players(standingAt(shooter, 400)), players(airborne(shooter, 300)), MS);
        assertEquals(10, particles.count(ParticleKind.JUMP_TRAIL));
        particles.clear();
        triggers.process(players(airborne(shooter, 300)), players(standingAt(shooter, 400)), 2 * MS);
        assertEquals(8, particles.count(ParticleKind.JUMP_TRAIL));

        particles.clear();
        long later = 9_000 * MS;
        triggers.process(players(standingAt(shooter, 400)), players(airborne(shooter, 300)), later);
        assertEquals(0, particles.count(ParticleKind.JUMP_TRAIL));
    }

    @Test
    void speedBoostAddsTrailWhileRunning() {
        ItemSnapshot boost = new ItemSnapshot(UUID.randomUUID(), "SPEED_BOOST", 120, 430);
        triggers.process(withItems(standingAt(shooter, 400), boost), players(standingAt(shooter, 400)), 0);
        particles.clear();
        WorldSnapshot running = players(player(shooter, 0, 100, true, 200, 100));
        triggers.process(running, running, 100 * MS);
        assertEquals(2, particles.count(ParticleKind.SPEED_TRAIL));
    }

    @Test
    void respawningClearsBoosts() {
        ItemSnapshot boost = new ItemSnapshot(UUID.randomUUID(), "SPEED_BOOST", 120, 430);
        triggers.process(withItems(standingAt(shooter, 400), boost), players(standingAt(shooter, 400)), 0);
        WorldSnapshot dead = players(player(shooter, 0, 100, true, 0, 0));
        WorldSnapshot respawned = players(player(shooter, 0, 100, true, 0, 100));
        triggers.process(dead, respawned, MS);
        particles.clear();
        WorldSnapshot running = players(player(shooter, 0, 100, true, 200, 100));
        triggers.process(running, running, 100 * MS);
        assertEquals(0, particles.count(ParticleKind.SPEED_TRAIL));
    }

    private PlayerSnapshot shielded(UUID id) {
        return new PlayerSnapshot(id, 0, "P0", 100, 400, 42, 64, true, true, 0, 100, 100, 0,
                CreateLobby.DEFAULT_CHARACTER, false);
    }

    @Test
    void meleeDamageEmitsHitBurstWithoutAnyAttackVisible() {
        WorldSnapshot before = players(player(victim, 1, 300, true, 0, 100));
        WorldSnapshot after = players(player(victim, 1, 300, true, 0, 85));
        triggers.process(before, after, 0);
        assertEquals(14, particles.count(ParticleKind.HIT));
    }

    @Test
    void fatalHitBurstsWhereThePlayerFellAndEndsBoosts() {
        WorldSnapshot before = players(player(victim, 1, 300, true, 0, 5));
        WorldSnapshot respawned = players(player(victim, 1, 50, true, 0, 100));
        triggers.process(before, respawned, 0);
        assertEquals(24, particles.count(ParticleKind.HIT));
    }

    @Test
    void shotAbsorbedByAShieldShowsAShieldBurst() {
        UUID attackId = UUID.randomUUID();
        WorldSnapshot before = world(List.of(player(shooter, 0, 100, true, 0, 100), shielded(victim)), List.of(),
                List.of(), List.of(ranged(attackId, 120)), List.of());
        WorldSnapshot after = players(player(shooter, 0, 100, true, 0, 100), shielded(victim));
        triggers.process(before, after, 0);
        assertEquals(8, particles.count(ParticleKind.POWERUP_SHIELD));
        assertEquals(0, particles.count(ParticleKind.HIT));
    }

    @Test
    void meleeSwingOverlappingAnNpcEmitsOneImpactPerSwing() {
        NPCSnapshot npc = new NPCSnapshot(1, 140, 400, 42, 64, "PATROL", true, 0, false);
        AttackSnapshot swing = new AttackSnapshot(UUID.randomUUID(), shooter, "MELEE", 1, 142, 400, 48, 64);
        WorldSnapshot before = world(List.of(player(shooter, 0, 100, true, 0, 100)), List.of(), List.of(npc),
                List.of(), List.of());
        WorldSnapshot during = world(List.of(player(shooter, 0, 100, true, 0, 100)), List.of(), List.of(npc),
                List.of(swing), List.of());
        triggers.process(before, during, 0);
        assertEquals(8, particles.count(ParticleKind.IMPACT));
        triggers.process(during, during, MS);
        assertEquals(8, particles.count(ParticleKind.IMPACT));
    }

    @Test
    void npcBeingStunnedEmitsImpact() {
        NPCSnapshot calm = new NPCSnapshot(1, 400, 416, 42, 64, "PATROL", true, 0, false);
        NPCSnapshot stunned = new NPCSnapshot(1, 400, 416, 42, 64, "PATROL", true, 0, true);
        PlayerSnapshot far = standingAt(shooter, 400);
        triggers.process(world(List.of(far), List.of(), List.of(calm), List.of(), List.of()),
                world(List.of(far), List.of(), List.of(stunned), List.of(), List.of()), 0);
        assertEquals(8, particles.count(ParticleKind.IMPACT));
    }

    @Test
    void shotLandingBetweenSnapshotsStillImpactsAnNpcThirtyUnitsAhead() {
        NPCSnapshot npc = new NPCSnapshot(1, 500, 416, 42, 64, "PATROL", true, 0, false);
        AttackSnapshot shot = new AttackSnapshot(UUID.randomUUID(), shooter, "RANGED", 1, 465, 440, 10, 10);
        WorldSnapshot before = world(List.of(standingAt(shooter, 400)), List.of(), List.of(npc), List.of(shot),
                List.of());
        WorldSnapshot after = world(List.of(standingAt(shooter, 400)), List.of(), List.of(npc), List.of(), List.of());
        triggers.process(before, after, 0);
        assertEquals(8, particles.count(ParticleKind.IMPACT));
    }

    @Test
    void shieldedPlayerKeepsOrbitingAuraMotes() {
        WorldSnapshot snapshot = players(shielded(shooter));
        triggers.process(snapshot, snapshot, 0);
        triggers.process(snapshot, snapshot, 50 * MS);
        assertEquals(4, particles.count(ParticleKind.SHIELD_AURA));
    }

    @Test
    void activeSpeedAndJumpBoostsAddNoOrbitingAura() {
        ItemSnapshot speed = new ItemSnapshot(UUID.randomUUID(), "SPEED_BOOST", 120, 430);
        ItemSnapshot jump = new ItemSnapshot(UUID.randomUUID(), "JUMP_BOOST", 125, 430);
        triggers.process(withItems(shielded(shooter), speed, jump), players(shielded(shooter)), 0);
        particles.clear();
        triggers.process(players(shielded(shooter)), players(shielded(shooter)), 50 * MS);
        assertEquals(2, particles.size());
        assertEquals(2, particles.count(ParticleKind.SHIELD_AURA));
    }

    @Test
    void collectedNotificationStartsABoostForThatNickname() {
        WorldSnapshot standing = players(standingAt(shooter, 400));
        triggers.itemCollected("P0 picked up JUMP_BOOST", standing, 0);
        triggers.process(standing, players(airborne(shooter, 300)), MS);
        assertEquals(10, particles.count(ParticleKind.JUMP_TRAIL));
        triggers.itemCollected("garbled", standing, 0);
        triggers.itemCollected("P0 picked up SPEED_BOOST", null, 0);
    }

    private WorldSnapshot withNpc(NPCSnapshot npc, AttackSnapshot... attacks) {
        return world(List.of(standingAt(shooter, 400)), List.of(), List.of(npc), List.of(attacks), List.of());
    }

    @Test
    void npcDyingBurstsOnceAndStaysSilentAfterwards() {
        NPCSnapshot living = new NPCSnapshot(1, 400, 416, 42, 64, "PATROL", true, 0, false, true);
        NPCSnapshot dead = new NPCSnapshot(1, 400, 416, 42, 64, "PATROL", true, 0, false, false);
        triggers.process(withNpc(living), withNpc(dead), 0);
        assertEquals(24, particles.count(ParticleKind.NPC_DEATH));
        triggers.process(withNpc(dead), withNpc(dead), MS);
        assertEquals(24, particles.count(ParticleKind.NPC_DEATH));
    }

    @Test
    void swingingAtADeadNpcEmitsNoImpact() {
        NPCSnapshot dead = new NPCSnapshot(1, 140, 400, 42, 64, "PATROL", true, 0, false, false);
        AttackSnapshot swing = new AttackSnapshot(UUID.randomUUID(), shooter, "MELEE", 1, 142, 400, 48, 64);
        triggers.process(withNpc(dead), withNpc(dead, swing), 0);
        assertEquals(0, particles.size());
    }

    @Test
    void newItemSparklesAndIdleItemsShimmer() {
        ItemSnapshot item = new ItemSnapshot(UUID.randomUUID(), "SHIELD", 500, 430);
        PlayerSnapshot far = standingAt(shooter, 400);
        triggers.process(players(far), withItems(far, item), 0);
        assertEquals(12, particles.count(ParticleKind.SPARKLE));
        particles.clear();
        triggers.process(withItems(far, item), withItems(far, item), 100 * MS);
        assertEquals(0, particles.size());
        triggers.process(withItems(far, item), withItems(far, item), 500 * MS);
        assertEquals(1, particles.count(ParticleKind.SPARKLE));
    }

    @Test
    void movingPlatformShedsRateLimitedDustWhileStaticOnesStaySilent() {
        triggers.process(withPlatform(100, 300), withPlatform(100, 300), 0);
        assertEquals(0, particles.size());
        triggers.process(withPlatform(100, 300), withPlatform(103, 300), MS);
        assertEquals(2, particles.count(ParticleKind.PLATFORM_DUST));
        triggers.process(withPlatform(103, 300), withPlatform(106, 300), 2 * MS);
        assertEquals(2, particles.count(ParticleKind.PLATFORM_DUST));
        triggers.process(withPlatform(106, 300), withPlatform(109, 300), 200 * MS);
        assertEquals(4, particles.count(ParticleKind.PLATFORM_DUST));
    }
}
