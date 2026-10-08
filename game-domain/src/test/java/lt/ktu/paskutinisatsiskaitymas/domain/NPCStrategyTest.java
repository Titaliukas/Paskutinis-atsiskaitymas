package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NPCStrategyTest {
    static Arena arena() {
        return new Arena("Test", 960, 540, List.of(new Platform(UUID.randomUUID(), new Position(0, 480), 960, 60)));
    }

    static NPC npc(double x) {
        return new NPC(1, new Position(x, 416), 42, 64, 10, 18, 10, 1.2, new PatrolStrategy(0, 900));
    }

    @Test
    void patrolReversesAndDoesNotOvershootBoundaries() {
        NPC npc = npc(99.9);
        npc.setActivityStrategy(new PatrolStrategy(90, 100));
        npc.advance(arena(), null, 0.1);
        assertEquals(100, npc.state().preciseX(), 1e-9);
        npc.advance(arena(), null, 0.1);
        assertTrue(npc.state().velocityX() < 0);
        for (int i = 0; i < 500; i++) {
            npc.advance(arena(), null, 0.1);
            assertTrue(npc.state().preciseX() >= 90 && npc.state().preciseX() <= 100);
        }
    }

    @Test
    void replacementUsesSameContextForChaseAttackFleeAndPatrol() {
        NPC npc = npc(100);
        NPC same = npc;
        npc.setTargetId(7);
        NPCTarget target = new NPCTarget(7, 150, 416, 42, 64);
        npc.setActivityStrategy(new ChaseStrategy(2));
        npc.advance(arena(), target, 0.1);
        assertEquals(20, npc.state().velocityX());
        npc.setActivityStrategy(new AttackStrategy());
        assertEquals(7, npc.advance(arena(), target, 0.1).attackTargetId());
        assertEquals(0, npc.state().velocityX());
        npc.setActivityStrategy(new FleeStrategy(3));
        npc.advance(arena(), target, 0.1);
        assertEquals(-30, npc.state().velocityX());
        npc.setActivityStrategy(new PatrolStrategy(0, 900));
        npc.advance(arena(), null, 0.1);
        assertEquals(10, npc.state().velocityX());
        assertSame(same, npc);
    }

    @Test
    void chaseAndFleeHandleBothSidesMissingAndCoincidentTargets() {
        NPC npc = npc(100);
        npc.setTargetId(7);
        NPCTarget left = new NPCTarget(7, 0, 416, 42, 64);
        NPCTarget coincident = new NPCTarget(7, 100, 416, 42, 64);
        assertTrue(new ChaseStrategy(1).decide(npc.state(), left, 0.1).velocityX() < 0);
        assertTrue(new FleeStrategy(1).decide(npc.state(), left, 0.1).velocityX() > 0);
        assertEquals(0, new ChaseStrategy(1).decide(npc.state(), coincident, 0.1).velocityX());
        assertTrue(new FleeStrategy(1).decide(npc.state(), coincident, 0.1).velocityX() < 0);
        for (NPCActivityStrategy strategy : List.of(new ChaseStrategy(1), new AttackStrategy(), new FleeStrategy(1))) {
            assertEquals(0, strategy.decide(npc.state(), null, 0.1).velocityX());
            assertFalse(strategy.decide(npc.state(), null, 0.1).hasAttackRequest());
            assertFalse(strategy.decide(npc.state(), new NPCTarget(8, 100, 416, 42, 64), 0.1).hasAttackRequest());
        }
    }

    @Test
    void attackRequestsRequireRangeCorrectTargetAndReadyCooldown() {
        NPC npc = npc(100);
        npc.setTargetId(7);
        AttackStrategy attack = new AttackStrategy();
        NPCTarget near = new NPCTarget(7, 160, 416, 42, 64);
        assertTrue(attack.decide(npc.state(), near, 0.1).hasAttackRequest());
        assertFalse(attack.decide(npc.state(), new NPCTarget(7, 160.01, 416, 42, 64), 0.1).hasAttackRequest());
        assertFalse(attack.decide(npc.state(), new NPCTarget(7, 100, 300, 42, 64), 0.1).hasAttackRequest());
        npc.recordAttackAttempt();
        assertEquals(1, npc.state().attackAttemptSequence());
        assertFalse(attack.decide(npc.state(), near, 0.1).hasAttackRequest());
        assertThrows(IllegalStateException.class, npc::recordAttackAttempt);
        npc.advance(arena(), null, 1.2);
        assertTrue(npc.state().attackReady());
    }

    @Test
    void attackRangeUsesPreciseGeometryRatherThanRoundedUMLView() {
        NPC npc = npc(100.49);
        npc.setTargetId(7);
        assertEquals(100, npc.state().x());
        assertTrue(new AttackStrategy().decide(npc.state(),
                new NPCTarget(7, 160.25, 416, 42, 64), 0.1).hasAttackRequest());
        assertFalse(new AttackStrategy().decide(npc.state(),
                new NPCTarget(7, 160.50, 416, 42, 64), 0.1).hasAttackRequest());
    }

    @Test
    void overflowIsRejectedWithoutPoisoningBodyState() {
        NPC npc = npc(100);
        assertThrows(IllegalArgumentException.class, () -> npc.advance(arena(), null, Double.MAX_VALUE));
        assertEquals(100, npc.state().preciseX());
        assertEquals(416, npc.state().preciseY());
        assertEquals(0, npc.state().velocityY());
    }

    @Test
    void preservesFractionalMotionAndResolvesGroundAndArenaEdges() {
        NPC npc = npc(100);
        for (int i = 0; i < 60; i++) {
            npc.advance(arena(), null, 1.0 / 60);
        }
        assertEquals(110, npc.state().preciseX(), 1e-9);
        assertEquals(110, npc.state().x());
        assertEquals(416, npc.state().preciseY());
        assertTrue(npc.state().grounded());
        NPC falling = new NPC(2, new Position(950 - 42, 0), 42, 64, 100, 18, 10, 1,
                new ChaseStrategy(3));
        falling.setTargetId(1);
        NPCTarget right = new NPCTarget(1, 1000, 416, 42, 64);
        for (int i = 0; i < 120; i++) {
            falling.advance(arena(), right, 1.0 / 60);
        }
        assertEquals(918, falling.state().preciseX());
        assertEquals(416, falling.state().preciseY());
        assertTrue(falling.state().grounded());
        falling.setActivityStrategy(new FleeStrategy(3));
        for (int i = 0; i < 240; i++) {
            falling.advance(arena(), right, 1.0 / 60);
        }
        assertEquals(0, falling.state().preciseX());
        assertFalse(falling.state().facingRight());
    }

    @Test
    void rejectsInvalidTimeAndNonfiniteGeometry() {
        NPC npc = npc(100);
        for (double seconds : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> npc.advance(arena(), null, seconds));
            assertThrows(IllegalArgumentException.class, () -> new AttackStrategy().decide(npc.state(), null, seconds));
        }
        assertThrows(IllegalArgumentException.class, () -> new NPCAction(Double.NaN, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new NPCAction(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChaseStrategy(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new FleeStrategy(0));
        assertThrows(IllegalArgumentException.class, () -> new PatrolStrategy(5, 5));
        assertThrows(IllegalArgumentException.class, () -> new NPCTarget(1, Double.NaN, 0, 42, 64));
        assertThrows(IllegalArgumentException.class, () -> new NPC(0, new Position(0, 0), 42, 64, 10, 18, 10, 1,
                new AttackStrategy()));
    }
}
