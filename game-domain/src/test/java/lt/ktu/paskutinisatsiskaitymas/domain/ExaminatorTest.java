package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExaminatorTest {
    static GameCharacter player(int slot, double x, CharacterType type) {
        return new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "P" + slot), slot,
                new Position(x, 416), true, type);
    }

    @Test
    void slotIteratorSkipsHolesInSlotOrderHasIndependentCursorsAndStandardExhaustion() {
        PlayerTargets targets = new PlayerTargets();
        GameCharacter last = player(3, 800, CharacterType.GOGIS);
        GameCharacter first = player(0, 100, CharacterType.PATASIJUS);
        targets.add(last);
        targets.add(first);
        Iterator<GameCharacter> a = targets.iterator();
        Iterator<GameCharacter> b = targets.iterator();
        assertTrue(a.hasNext());
        assertTrue(a.hasNext());
        assertSame(first, a.next());
        assertSame(first, b.next());
        assertSame(last, a.next());
        assertFalse(a.hasNext());
        assertFalse(a.hasNext());
        assertThrows(NoSuchElementException.class, a::next);
        assertSame(last, b.next());
        targets.remove(first.playerId());
        targets.add(player(1, 200, CharacterType.VACIJUS));
        assertEquals(1, targets.iterator().next().state().slot());
        assertThrows(UnsupportedOperationException.class, () -> targets.view().clear());
    }

    private static Examinator ability(GameCharacter owner, List<Iterable<? extends AutoTarget>> sources) {
        return new Examinator(owner, sources, NPCStrategyTest.arena());
    }

    private static void firstAim(Examinator ability) { ability.update(1.6); }
    private static void firstFire(Examinator ability) { ability.update(2.1); }

    @Test
    void sequentialPassTraversesLiveSourcesInOrderOnceAndRecordsPreHitEndpoints() {
        PlayerTargets players = new PlayerTargets();
        NpcTargets npcs = new NpcTargets();
        Projectiles shots = new Projectiles();
        GameCharacter owner = player(0, 400, CharacterType.VACIJUS);
        players.add(owner);
        Examinator ability = ability(owner, List.of(players, npcs, shots, players));
        GameCharacter left = player(1, 100, CharacterType.PATASIJUS);
        GameCharacter dodge = player(2, 600, CharacterType.GOGIS);
        GameCharacter right = player(3, 800, CharacterType.JURGELINIS);
        players.add(left); players.add(dodge); players.add(right);
        left.takeDamage(80);
        NPC npc = NPCStrategyTest.npc(500);
        npc.takeDamage(25);
        npcs.put(1, npc);
        shots.spawn(npc.state(), new NPCTarget(7, 100, 200, 42, 64), ProjectileType.BOOK);
        assertTrue(ability.activate());
        dodge.setProne(true);
        firstAim(ability);
        assertEquals("AIM", ability.state().phase());
        assertEquals(20, left.state().health());
        assertEquals(100, right.state().health());
        ability.update(0.5);
        ExaminatorShot recorded = ability.state().shots().getFirst();
        assertEquals(new Position(121, 448), recorded.endpoint());
        assertEquals(new Position(487, 267), recorded.origin());
        assertEquals(100, left.state().health());
        assertEquals(1, left.respawnSequence());
        assertEquals(100, right.state().health(), "One target at a time");
        ability.update(0.9);
        assertEquals(70, right.state().health());
        dodge.setProne(false); // Already considered while skipped; standing later cannot re-enter this pass.
        ability.update(0.9);
        assertFalse(npc.alive());
        npcs.removeInactive(); // Structural changes between ticks never invalidate retained iterators.
        ability.update(0.9);
        assertFalse(shots.iterator().next().isActive());
        shots.removeInactive();
        ability.update(0.4);
        assertEquals("IDLE", ability.state().phase());
        assertEquals(4, ability.state().shotSequence());
        assertEquals(100, left.state().health(), "Respawned object cannot be hit twice");
        assertEquals(100, dodge.state().health());
        assertEquals(100, owner.state().health());
        assertEquals(new Position(121, 448), recorded.endpoint(), "Detached after respawn and cleanup");
    }

    @Test
    void cooldownStartsAtAcceptanceAndActiveGuardSurvivesCooldownExpiry() {
        GameCharacter owner = player(0, 100, CharacterType.PATASIJUS);
        ArrayList<AutoTarget> growing = new ArrayList<>();
        growing.add(target(new Position(800, 300), new ArrayList<>()));
        Examinator ability = ability(owner, List.of(growing));
        assertTrue(ability.activate());
        assertEquals(30, ability.state().cooldownRemaining());
        ability.update(0.25);
        assertFalse(ability.activate());
        assertEquals(0.55, ability.state().phaseRemaining(), 1e-9);
        for (int i = 0; i < 35; i++) {
            growing.add(target(new Position(800, 300), new ArrayList<>()));
            ability.update(0.9);
        }
        assertEquals(0, ability.state().cooldownRemaining());
        assertNotEquals("IDLE", ability.state().phase());
        assertFalse(ability.activate());
        // A source that keeps producing fresh candidates still has a finite pass.
        for (int i = 0; i < 105; i++) {
            growing.add(target(new Position(800, 300), new ArrayList<>()));
            ability.update(0.9);
        }
        assertEquals("IDLE", ability.state().phase());
        assertTrue(ability.activate());
    }

    @Test
    void flightCapturesLaunchAndActualArenaCenterWithoutPrematureDamageOrOwnerDragging() {
        for (Arena arena : List.of(new ArenaDirector().construct(new TrainingArenaBuilder()),
                new ArenaDirector().construct(new CombatArenaBuilder()),
                TestArenas.geometry("Wide", 1200, 700, List.of()))) {
            GameCharacter owner = player(0, 100, CharacterType.PATASIJUS);
            GameCharacter victim = player(1, 800, CharacterType.GOGIS);
            Examinator ability = new Examinator(owner, List.of(List.of(victim)), arena);
            ability.activate();
            Position launch = ability.state().position();
            owner.moveWithPlatform(100, 0);
            ability.update(0.4);
            assertEquals((launch.x() + arena.width() / 2) / 2, ability.state().position().x());
            ability.update(0.4);
            assertEquals(new Position(arena.width() / 2, arena.height() / 2), ability.state().position());
            assertEquals("INSPECT", ability.state().phase());
            ability.update(1.29);
            assertEquals(100, victim.state().health());
            ability.update(0.01);
            assertEquals(88, victim.state().health());
            assertEquals("FIRE", ability.state().phase());
        }
    }

    @Test
    void proneAndRespawnCancelEveryActivePhaseWithoutRefundAndClearFeedback() {
        for (double elapsed : List.of(0.1, 0.9, 1.5, 1.8, 2.1)) {
            for (boolean respawn : List.of(true, false)) {
                GameCharacter owner = player(0, 100, CharacterType.PATASIJUS);
                Examinator ability = ability(owner, List.of(List.of(player(1, 800, CharacterType.GOGIS))));
                ability.activate(); ability.update(elapsed);
                if (respawn) { owner.takeDamage(100); }
                else { owner.setProne(true); owner.setProne(false); }
                ability.update(0.1);
                assertEquals("IDLE", ability.state().phase());
                assertTrue(ability.state().shots().isEmpty());
                assertEquals(30 - elapsed - 0.1, ability.state().cooldownRemaining(), 1e-8);
                assertFalse(ability.activate());
            }
        }
    }

    @Test
    void everyLoadoutReusesRangedEffectsAndShieldsStillBlockThem() {
        for (CharacterType type : CharacterType.values()) {
            GameCharacter owner = player(0, 400, type);
            GameCharacter shield = player(1, 100, CharacterType.PATASIJUS);
            GameCharacter victim = player(2, 800, CharacterType.PATASIJUS);
            GameCharacter expected = player(3, 800, CharacterType.PATASIJUS);
            shield.applyShield(5);
            Examinator ability = ability(owner, List.of(List.of(shield, victim)));
            ability.activate(); ability.update(3);
            type.loadout().effectsFor(AttackType.RANGED).forEach(effect -> effect.applyTo(expected, 1));
            assertEquals(expected.state().health(), victim.state().health());
            assertEquals(expected.stats().speedMultiplier(), victim.stats().speedMultiplier());
            assertEquals(100, shield.state().health());
            assertEquals(1, shield.stats().speedMultiplier());
            victim.setProne(true);
            assertTrue(victim.takeDamage(1));
        }
    }

    @Test
    void freshIteratorsHandleRemovalAdditionRespawnDodgeAndReplacementDuringAim() {
        for (String change : List.of("remove", "respawn", "prone")) {
            GameCharacter owner = player(0, 400, CharacterType.VACIJUS);
            GameCharacter victim = player(1, 100, CharacterType.PATASIJUS);
            PlayerTargets players = new PlayerTargets();
            players.add(victim);
            NpcTargets npcs = new NpcTargets();
            Examinator ability = ability(owner, List.of(players, npcs));
            ability.activate(); firstAim(ability);
            if (change.equals("remove")) { players.remove(victim.playerId()); }
            else if (change.equals("respawn")) { victim.takeDamage(100); }
            else { victim.setProne(true); }
            NPC npc = NPCStrategyTest.npc(500);
            npcs.put(1, npc);
            ability.update(1.1);
            assertEquals(100, victim.state().health());
            assertEquals(20, npc.health());
            assertEquals(1, ability.state().shotSequence());
        }
        NpcTargets npcs = new NpcTargets();
        NPC old = NPCStrategyTest.npc(500);
        npcs.put(1, old);
        Examinator ability = ability(player(0, 400, CharacterType.VACIJUS), List.of(npcs));
        ability.activate(); firstAim(ability);
        NPC replacement = NPCStrategyTest.npc(100);
        npcs.put(1, replacement);
        ability.update(1);
        assertEquals(50, old.health());
        assertEquals(20, replacement.health());
    }

    @Test
    void projectileReplacementAbandonsOldLockAndExhaustedSourcesAreNotRevisited() {
        Projectiles shots = new Projectiles();
        NPC npc = new NPC(1, new Position(100, 200), 42, 64, 10, 18, 10, 1.2, new PatrolStrategy(0, 900));
        shots.spawn(npc.state(), new NPCTarget(7, 800, 200, 42, 64), ProjectileType.BOOK);
        Projectile outbound = shots.iterator().next();
        PlayerTargets players = new PlayerTargets();
        Examinator ability = ability(player(0, 400, CharacterType.GOGIS), List.of(players, shots));
        ability.activate(); firstAim(ability);
        players.add(player(1, 100, CharacterType.GOGIS)); // Earlier exhausted source stays exhausted.
        shots.advance(NPCStrategyTest.arena(), Map.of(1, npc), List.of(), 3);
        Projectile returning = shots.iterator().next();
        assertNotSame(outbound, returning);
        assertTrue(returning.state().returning());
        ability.update(0.7);
        assertFalse(returning.isActive());
        assertEquals(1, ability.state().shotSequence());
        assertEquals(100, players.iterator().next().state().health());
        shots.advance(NPCStrategyTest.arena(), Map.of(1, npc), List.of(), 2);
        assertTrue(shots.states().isEmpty());
        assertFalse(npc.state().stunned());
    }

    @Test
    void allShotVectorsUseCentralEmitterAndOvershootMatchesSmallSteps() {
        for (Position point : List.of(new Position(50, 50), new Position(480, 50), new Position(850, 50),
                new Position(50, 448), new Position(850, 448), new Position(487, 267))) {
            List<Integer> directions = new ArrayList<>();
            Examinator ability = ability(player(0, 400, CharacterType.PATASIJUS),
                    List.of(List.of(target(point, directions))));
            ability.activate(); firstFire(ability);
            assertEquals(List.of(point.x() >= 487 ? 1 : -1), directions);
            assertEquals(point, ability.state().shots().getFirst().endpoint());
            assertEquals(new Position(487, 267), ability.state().shots().getFirst().origin());
            assertThrows(UnsupportedOperationException.class, () -> ability.state().shots().clear());
        }
        List<AutoTarget> targets = List.of(target(new Position(50, 200), new ArrayList<>()),
                target(new Position(850, 400), new ArrayList<>()));
        Examinator large = ability(player(0, 400, CharacterType.PATASIJUS), List.of(targets));
        Examinator small = ability(player(0, 400, CharacterType.PATASIJUS), List.of(targets));
        large.activate(); small.activate(); large.update(3.05);
        for (int i = 0; i < 305; i++) { small.update(0.01); }
        assertEquals(2, large.state().shotSequence());
        assertEquals(large.state().phase(), small.state().phase());
        assertEquals(large.state().shots().getFirst().remainingSeconds(),
                small.state().shots().getFirst().remainingSeconds(), 1e-8);
    }

    private static AutoTarget target(Position point, List<Integer> directions) {
        return new AutoTarget() {
            public boolean canBeTargetedBy(GameCharacter source) { return true; }
            public Position aimPoint() { return point; }
            public void receiveHit(List<AttackEffect> effects, int direction) { directions.add(direction); }
        };
    }

    @Test
    void sourceAndTargetCleanupMakeRemovedProjectilesUntargetable() {
        for (boolean removeSource : List.of(true, false)) {
            Projectiles projectiles = new Projectiles();
            NPC source = NPCStrategyTest.npc(100);
            projectiles.spawn(source.state(), new NPCTarget(7, 800, 416, 42, 64), ProjectileType.BOOK);
            Projectile projectile = projectiles.iterator().next();
            if (removeSource) { projectiles.removeSource(1); }
            else { projectiles.removeTarget(7); }
            assertFalse(projectile.canBeTargetedBy(null));
            assertTrue(projectiles.states().isEmpty());
        }
    }

    @Test
    void proneBlocksVoluntaryMotionJumpAndAttacksButKeepsPhysicsAndClearsOnRespawnOrLostSupport() {
        GameCharacter body = player(0, 100, CharacterType.GOGIS);
        body.setProne(true);
        body.advance(new MovementInput(false, true, true, true), NPCStrategyTest.arena(), 0.1);
        assertEquals(100, body.x());
        assertTrue(body.state().grounded());
        assertTrue(body.isProne());
        assertFalse(body.tryUseCooldown(AttackType.MELEE));
        assertFalse(body.tryUseCooldown(AttackType.RANGED));
        body.applyImpulse(100, -100);
        body.advance(MovementInput.NONE, NPCStrategyTest.arena(), 0.1);
        assertTrue(body.x() > 100);
        assertFalse(body.isProne());
        body.setProne(true);
        assertFalse(body.isProne(), "Cannot enter prone in the air");
        body.takeDamage(100);
        assertTrue(body.state().grounded());
        assertFalse(body.isProne());
    }

    @Test
    void lostSupportClearsProneWhileTimedEffectsAndWindupMovementContinue() {
        GameCharacter body = player(0, 100, CharacterType.GOGIS);
        body.applyShield(0.05);
        body.setProne(true);
        body.moveWithPlatform(300, 0);
        Arena ledge = TestArenas.geometry("Ledge", 960, 540, List.of(new StationaryPlatform(UUID.randomUUID(),
                new Position(0, 480), 200, 60)));
        body.advance(MovementInput.NONE, ledge, 0.1);
        assertFalse(body.isProne());
        assertFalse(body.state().grounded());
        assertFalse(body.state().shielded());
        GameCharacter owner = player(0, 100, CharacterType.GOGIS);
        Examinator ability = ability(owner, List.of());
        ability.activate();
        owner.advance(new MovementInput(false, true, true), NPCStrategyTest.arena(), 0.2);
        ability.update(0.2);
        assertTrue(owner.x() > 100);
        assertTrue(owner.y() < 416);
        assertNotEquals(owner.x() + 21, ability.state().position().x());
        ability.update(0.6);
        assertEquals(new Position(480, 270), ability.state().position());
    }

    @Test
    void sessionsRetainLiveAbilitiesRejectForeignSourcesAndRemoveOwnerOnDeparture() {
        GameSession session = new GameSession(UUID.randomUUID(), NPCStrategyTest.arena());
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        session.addPlayer(new Player(owner, "Owner"), 0, CharacterType.VACIJUS);
        session.startExaminator(owner);
        session.addPlayer(new Player(guest, "Guest"), 1, CharacterType.PATASIJUS);
        session.advance(Map.of(), 2.1);
        assertEquals(70, session.characters().get(1).health());
        assertThrows(IllegalArgumentException.class,
                () -> session.startExaminator(player(0, 100, CharacterType.VACIJUS)));
        session.removePlayer(guest);
        assertEquals(1, session.examinators().size());
        assertFalse(session.startExaminator(guest));
        GameCharacter impostor = new GameCharacter(UUID.randomUUID(), new Player(owner, "Impostor"), 0,
                new Position(100, 416), true, CharacterType.GOGIS);
        assertThrows(IllegalArgumentException.class, () -> session.startExaminator(impostor));
        session.removePlayer(owner);
        assertTrue(session.examinators().isEmpty());
    }
}
