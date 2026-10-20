package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectilesTest {
    private final Projectiles projectiles = new Projectiles();
    private final NPC source = npc(1, 100, 200);
    private final NPCTarget target = new NPCTarget(7, 800, 200, 42, 64);

    private static NPC npc(int id, double x, double y) {
        return new NPC(id, new Position(x, y), 42, 64, 10, 18, 10, 1.2, new PatrolStrategy(0, 900));
    }

    private static GameCharacter player(double x, double y) {
        return new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "Player"),
                0, new Position(x, y), false, CharacterType.PATASIJUS);
    }

    @Test
    void prototypeCopyHasFreshIdentityAndIndependentMotionAndLifetime() {
        Projectile original = new Projectile(UUID.randomUUID(), 1, 7, ProjectileType.BOOK,
                new Position(100, 200), 360, 0, 6, false);
        Prototype<Projectile> prototype = original;
        Projectile clone = prototype.clone();
        assertNotEquals(original.state().id(), clone.state().id());
        assertEquals(original.state().position(), clone.state().position());
        clone.move(1);
        clone.aimAt(0, 0);
        assertEquals(new Position(100, 200), original.state().position());
        assertEquals(6, original.state().remainingSeconds());
        assertEquals(360, original.state().velocityX());
        assertEquals(5, clone.state().remainingSeconds());
    }

    @Test
    void sideWallCreatesOneCloneAndUsesRemainingTickTravelThenStunsOnlySource() {
        assertTrue(projectiles.spawn(source.state(), target, ProjectileType.BOOK));
        ProjectileState original = projectiles.states().getFirst();
        projectiles.advance(NPCStrategyTest.arena(), Map.of(1, source), List.of(), 3);
        ProjectileState returned = projectiles.states().getFirst();
        assertTrue(returned.returning());
        assertNotEquals(original.id(), returned.id());
        assertEquals(original.type(), returned.type());
        assertEquals(3, returned.remainingSeconds(), 1e-9);
        assertEquals(703, returned.position().x(), 1e-9);
        assertEquals(-360, returned.velocityX(), 1e-9);
        GameCharacter crossedPlayer = player(500, 200);
        NPC otherNPC = npc(2, 400, 200);
        projectiles.advance(NPCStrategyTest.arena(), Map.of(1, source, 2, otherNPC), List.of(crossedPlayer), 2);
        assertTrue(projectiles.states().isEmpty());
        assertTrue(source.state().stunned());
        assertFalse(otherNPC.state().stunned());
        assertFalse(crossedPlayer.state().slowed());
    }

    @Test
    void leftWallAlsoReturnsAndCannotCloneAgainAfterMissingTheMovingThrower() {
        NPC rightSource = npc(1, 800, 200);
        projectiles.spawn(rightSource.state(), new NPCTarget(7, 100, 200, 42, 64), ProjectileType.DIPLOMA);
        projectiles.advance(NPCStrategyTest.arena(), Map.of(1, rightSource), List.of(), 2.5);
        ProjectileState returned = projectiles.states().getFirst();
        assertTrue(returned.returning());
        assertEquals(360, returned.velocityX(), 1e-9);
        assertEquals(95, returned.position().x(), 1e-9);
        // A replacement body at a different height represents a thrower dodging the straight return.
        projectiles.advance(NPCStrategyTest.arena(), Map.of(1, npc(1, 800, 0)), List.of(), 3);
        assertTrue(projectiles.states().isEmpty(), "Second wall removes the return");
    }

    @Test
    void returnAimsAtCurrentSourcePositionAtWallAndDoesNotHomeAfterward() {
        projectiles.spawn(source.state(), target, ProjectileType.BOOK);
        NPC moved = npc(1, 200, 100);
        projectiles.advance(NPCStrategyTest.arena(), Map.of(1, moved), List.of(), 2.4);
        ProjectileState returned = projectiles.states().getFirst();
        double distance = Math.hypot(221 - 952, 132 - 232);
        assertEquals((221 - 952) / distance * 360, returned.velocityX(), 1e-9);
        assertEquals((132 - 232) / distance * 360, returned.velocityY(), 1e-9);
        projectiles.advance(NPCStrategyTest.arena(), Map.of(1, source), List.of(), 0.1);
        assertEquals(returned.velocityY(), projectiles.states().getFirst().velocityY());
    }

    @Test
    void sweptShotHitsNearestPlayerRegardlessOfListOrderAndIgnoresNPCs() {
        projectiles.spawn(source.state(), target, ProjectileType.BOOK);
        GameCharacter near = player(300, 200);
        GameCharacter far = player(500, 200);
        NPC obstacle = npc(2, 200, 200);
        projectiles.advance(NPCStrategyTest.arena(), Map.of(1, source, 2, obstacle), List.of(far, near), 2);
        assertTrue(near.state().slowed());
        assertFalse(far.state().slowed());
        assertFalse(obstacle.state().stunned());
        assertTrue(projectiles.states().isEmpty());
    }

    @Test
    void shieldAbsorbsShotWithoutSlowingOrContinuingToAnotherPlayer() {
        projectiles.spawn(source.state(), target, ProjectileType.BOOK);
        GameCharacter shielded = player(300, 200);
        shielded.applyShield(8);
        GameCharacter behind = player(500, 200);
        projectiles.advance(NPCStrategyTest.arena(), Map.of(1, source), List.of(behind, shielded), 2);
        assertFalse(shielded.state().slowed());
        assertFalse(behind.state().slowed());
        assertTrue(projectiles.states().isEmpty());
    }

    @Test
    void platformAbsorbsBeforePlayerAndCeilingAndGroundAbsorbWithoutCloning() {
        Arena blocked = new Arena("Blocked", 960, 540, List.of(
                new Platform(UUID.randomUUID(), new Position(250, 200), 10, 100)));
        projectiles.spawn(source.state(), target, ProjectileType.BOOK);
        GameCharacter behind = player(300, 200);
        projectiles.advance(blocked, Map.of(1, source), List.of(behind), 2);
        assertFalse(behind.state().slowed());
        assertTrue(projectiles.states().isEmpty());
        for (double y : new double[]{0, 500}) {
            projectiles.spawn(source.state(), new NPCTarget(7, 100, y, 42, 64), ProjectileType.BOOK);
            projectiles.advance(NPCStrategyTest.arena(), Map.of(1, source), List.of(), 2);
            assertTrue(projectiles.states().isEmpty());
        }
    }

    @Test
    void tangentialRoundedCornerDoesNotCollideWithTheExpandedBoxCorner() {
        NPC high = npc(1, 100, 61); // Shot center y=93, seven units above platform top.
        Arena arena = new Arena("Corner", 960, 540, List.of(
                new Platform(UUID.randomUUID(), new Position(200, 100), 20, 20)));
        projectiles.spawn(high.state(), new NPCTarget(7, 800, 61, 42, 64), ProjectileType.BOOK);
        projectiles.advance(arena, Map.of(1, high), List.of(), (193.0 - 121) / 360);
        assertEquals(1, projectiles.states().size(), "Circle has not reached rounded corner at x=193");
        projectiles.advance(arena, Map.of(1, high), List.of(), 0.02);
        assertTrue(projectiles.states().isEmpty());
    }

    @Test
    void sixSecondLifetimeExpiresBeforeAnyWallInLargeArenaAndCapNeverExceedsEight() {
        Arena large = new Arena("Large", 10000, 1000, List.of());
        for (int i = 0; i < 8; i++) {
            assertTrue(projectiles.spawn(source.state(), target, ProjectileType.BOOK));
        }
        assertFalse(projectiles.spawn(source.state(), target, ProjectileType.BOOK));
        var detached = projectiles.states();
        assertThrows(UnsupportedOperationException.class, detached::clear);
        projectiles.advance(large, Map.of(1, source), List.of(), 6);
        assertTrue(projectiles.states().isEmpty());
        assertEquals(8, detached.size());
    }

    @Test
    void removalBySourceOrTargetAndMissingSourceClearsFlight() {
        projectiles.spawn(source.state(), target, ProjectileType.BOOK);
        projectiles.removeTarget(7);
        assertTrue(projectiles.states().isEmpty());
        projectiles.spawn(source.state(), target, ProjectileType.BOOK);
        projectiles.removeSource(1);
        assertTrue(projectiles.states().isEmpty());
        projectiles.spawn(source.state(), target, ProjectileType.BOOK);
        projectiles.advance(NPCStrategyTest.arena(), Map.of(), List.of(), 0.1);
        assertTrue(projectiles.states().isEmpty());
    }
}
