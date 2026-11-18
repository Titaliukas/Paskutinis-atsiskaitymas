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

    @Test
    void oneSharedVolleyTraversesLiveSourcesOnceAndRecordsPreRespawnAndDeathEndpoints() {
        PlayerTargets players = new PlayerTargets();
        NpcTargets npcs = new NpcTargets();
        Projectiles shots = new Projectiles();
        GameCharacter owner = player(0, 400, CharacterType.VACIJUS);
        players.add(owner);
        Examinator ability = new Examinator(owner, List.of(players, npcs, shots, players));
        // Added after construction: the retained source references must see these exact entities.
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
        dodge.setProne(true); // Eligibility is evaluated at fire time.
        ability.update(0.49);
        assertEquals(20, left.state().health());
        ability.update(0.01);
        assertEquals(100, left.state().health());
        assertEquals(1, left.respawnSequence());
        assertEquals(70, right.state().health());
        assertEquals(100, dodge.state().health());
        assertEquals(100, owner.state().health());
        assertFalse(npc.alive());
        assertFalse(shots.iterator().next().isActive());
        assertEquals(4, ability.state().endpoints().size());
        assertEquals(new Position(121, 448), ability.state().endpoints().getFirst());
        assertFalse(ability.state().endpoints().contains(dodge.aimPoint()));
        npcs.removeInactive(); shots.removeInactive();
        assertFalse(npcs.iterator().hasNext());
        assertFalse(shots.iterator().hasNext());
    }

    @Test
    void cooldownStartsAtAcceptanceDoesNotRestartAndOldFeedbackIsDetached() {
        GameCharacter owner = player(0, 100, CharacterType.PATASIJUS);
        GameCharacter victim = player(1, 800, CharacterType.VACIJUS);
        Examinator ability = new Examinator(owner, List.of(List.of(owner, victim)));
        assertTrue(ability.activate());
        assertEquals(10, ability.state().cooldownRemaining());
        ability.update(0.25);
        assertFalse(ability.activate());
        assertEquals(0.25, ability.state().phaseRemaining());
        ability.update(0.25);
        ExaminatorState fired = ability.state();
        assertEquals(88, victim.state().health());
        assertThrows(UnsupportedOperationException.class, () -> fired.endpoints().clear());
        ability.update(0.25);
        assertEquals("IDLE", ability.state().phase());
        assertTrue(ability.state().endpoints().isEmpty());
        assertEquals(1, fired.endpoints().size());
        ability.update(9.25);
        assertEquals(1, ability.state().volleySequence());
        assertEquals(88, victim.state().health());
        assertTrue(ability.activate());
    }

    @Test
    void explicitRespawnAndEnteringProneCancelWindupWithoutRefundEvenAtSameCoordinates() {
        for (boolean respawn : List.of(true, false)) {
            GameCharacter owner = player(0, 100, CharacterType.PATASIJUS);
            Examinator ability = new Examinator(owner, List.of(List.of(player(1, 800, CharacterType.GOGIS))));
            ability.activate();
            if (respawn) {
                owner.takeDamage(100); // Same spawn coordinates and full health again.
            } else {
                owner.setProne(true);
                owner.setProne(false);
            }
            ability.update(0.5);
            assertEquals(0, ability.state().volleySequence());
            assertEquals(9.5, ability.state().cooldownRemaining());
            assertFalse(ability.activate());
        }
    }

    @Test
    void everyLoadoutReusesRangedEffectsShieldsBlockThemAndProneIsNotGeneralDamageImmunity() {
        for (CharacterType type : CharacterType.values()) {
            GameCharacter owner = player(0, 400, type);
            GameCharacter shield = player(1, 100, CharacterType.PATASIJUS);
            GameCharacter victim = player(2, 800, CharacterType.PATASIJUS);
            GameCharacter expected = player(3, 800, CharacterType.PATASIJUS);
            shield.applyShield(2);
            Examinator ability = new Examinator(owner, List.of(List.of(shield, victim)));
            ability.activate(); ability.update(0.5);
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
    void directionIsDerivedSeparatelyForEachTarget() {
        GameCharacter owner = player(0, 400, CharacterType.PATASIJUS);
        GameCharacter left = player(1, 100, CharacterType.PATASIJUS);
        GameCharacter right = player(2, 800, CharacterType.PATASIJUS);
        left.receiveHit(List.of(new KnockbackEffect(100, -20)), -1);
        right.receiveHit(List.of(new KnockbackEffect(100, -20)), 1);
        left.advance(MovementInput.NONE, NPCStrategyTest.arena(), 0.01);
        right.advance(MovementInput.NONE, NPCStrategyTest.arena(), 0.01);
        assertTrue(left.x() < 100);
        assertTrue(right.x() > 800);
        // Target contract is polymorphic; traversal never inspects target or collection classes.
        ArrayList<Integer> directions = new ArrayList<>();
        AutoTarget targetLeft = target(new Position(50, 200), directions);
        AutoTarget targetRight = target(new Position(850, 200), directions);
        Examinator ability = new Examinator(owner, List.of(List.of(targetLeft, targetRight)));
        ability.activate(); ability.update(0.5);
        assertEquals(List.of(-1, 1), directions);
    }

    private static AutoTarget target(Position point, List<Integer> directions) {
        return new AutoTarget() {
            public boolean canBeTargetedBy(GameCharacter source) { return true; }
            public Position aimPoint() { return point; }
            public void receiveHit(List<AttackEffect> effects, int direction) { directions.add(direction); }
        };
    }

    @Test
    void returningProjectilesAreInterceptedBeforeTheyCanFlyAndStunTheirSource() {
        Projectiles shots = new Projectiles();
        NPC npc = new NPC(1, new Position(100, 200), 42, 64, 10, 18, 10, 1.2, new PatrolStrategy(0, 900));
        shots.spawn(npc.state(), new NPCTarget(7, 800, 200, 42, 64), ProjectileType.BOOK);
        shots.advance(NPCStrategyTest.arena(), Map.of(1, npc), List.of(), 3);
        Projectile returning = shots.iterator().next();
        assertTrue(returning.state().returning());
        Examinator ability = new Examinator(player(0, 400, CharacterType.GOGIS), List.of(shots));
        ability.activate(); ability.update(0.5);
        assertFalse(returning.canBeTargetedBy(null));
        shots.advance(NPCStrategyTest.arena(), Map.of(1, npc), List.of(), 2);
        assertTrue(shots.states().isEmpty());
        assertFalse(npc.state().stunned());
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
        Examinator ability = new Examinator(owner, List.of());
        ability.activate();
        owner.advance(new MovementInput(false, true, true), NPCStrategyTest.arena(), 0.2);
        ability.update(0.2);
        assertTrue(owner.x() > 100);
        assertTrue(owner.y() < 416);
        assertEquals(owner.x() + 21, ability.state().position().x());
        assertEquals(owner.y() - 28, ability.state().position().y());
        ability.update(0.3);
        assertEquals(ability.state().position(), ability.state().shotOrigin());
    }

    @Test
    void sessionsRetainLiveAbilitiesRejectForeignSourcesAndRemoveOwnerOnDeparture() {
        GameSession session = new GameSession(UUID.randomUUID(), NPCStrategyTest.arena());
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        session.addPlayer(new Player(owner, "Owner"), 0, CharacterType.VACIJUS);
        session.startExaminator(owner);
        session.addPlayer(new Player(guest, "Guest"), 1, CharacterType.PATASIJUS);
        session.advance(Map.of(), 0.5);
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
