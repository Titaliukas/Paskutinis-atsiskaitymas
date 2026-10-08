package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NPCBehaviorControllerTest {
    private final NPC npc = NPCStrategyTest.npc(100);
    private final NPCBehaviorController controller = new NPCBehaviorController(npc, new PatrolStrategy(0, 900),
            200, 0.5, 150, 1, 0.8);
    private final NPCTarget target = new NPCTarget(7, 180, 416, 42, 64);

    private void detect() {
        controller.update(List.of(target), 0.5);
        assertEquals(NPCActivity.CHASE, controller.activity());
    }

    @Test
    void requiresContinuousDetectionAndBreaksNearestTiesByNumericId() {
        controller.update(List.of(target), 0.3);
        assertEquals(NPCActivity.PATROL, controller.activity());
        controller.update(List.of(), 0.3);
        assertNull(npc.state().targetId());
        controller.update(List.of(target), 0.3);
        assertEquals(NPCActivity.PATROL, controller.activity());
        controller.update(List.of(target), 0.2);
        assertEquals(NPCActivity.CHASE, controller.activity());
        controller.switchActivity(NPCActivity.PATROL);
        controller.update(List.of(target, new NPCTarget(2, 180, 416, 42, 64)), 0.5);
        assertEquals(2, npc.state().targetId());
    }

    @Test
    void changingDetectionCandidateResetsContinuousProgress() {
        controller.update(List.of(target), 0.3);
        NPCTarget replacement = new NPCTarget(2, 170, 416, 42, 64);
        controller.update(List.of(replacement), 0.3);
        assertEquals(NPCActivity.PATROL, controller.activity());
        controller.update(List.of(replacement), 0.2);
        assertEquals(NPCActivity.CHASE, controller.activity());
        assertEquals(2, npc.state().targetId());
    }

    @Test
    void chaseTimeoutAndTargetLossReturnToPatrol() {
        detect();
        controller.update(List.of(target), 1);
        assertEquals(NPCActivity.PATROL, controller.activity());
        assertNull(npc.state().targetId());
        detect();
        controller.update(List.of(), 0.1);
        assertEquals(NPCActivity.PATROL, controller.activity());
        assertNull(npc.state().targetId());
    }

    @Test
    void attackFeedbackSwitchesToFleeOnlyAfterActualDamageThenTimesOut() {
        detect();
        NPCTarget near = new NPCTarget(7, 150, 416, 42, 64);
        controller.update(List.of(near), 0.1);
        assertEquals(NPCActivity.ATTACK, controller.activity());
        controller.onAttackResolved(false);
        assertEquals(NPCActivity.ATTACK, controller.activity());
        controller.onAttackResolved(true);
        assertEquals(NPCActivity.FLEE, controller.activity());
        controller.update(List.of(near), 0.1);
        npc.advance(NPCStrategyTest.arena(), near, 0.1);
        assertTrue(npc.state().facingRight());
        assertTrue(npc.state().velocityX() < 0, "Flee backs away while facing the threat");
        controller.update(List.of(near), 0.7);
        assertEquals(NPCActivity.PATROL, controller.activity());
        assertNull(npc.state().targetId());
    }

    @Test
    void safeDistanceEndsFleeAndLostAttackRangeResumesChase() {
        detect();
        controller.update(List.of(new NPCTarget(7, 150, 416, 42, 64)), 0.1);
        controller.update(List.of(target), 0.1);
        assertEquals(NPCActivity.CHASE, controller.activity());
        controller.update(List.of(new NPCTarget(7, 150, 416, 42, 64)), 0.1);
        controller.onAttackResolved(true);
        controller.update(List.of(new NPCTarget(7, 292, 416, 42, 64)), 0.1);
        assertEquals(NPCActivity.PATROL, controller.activity());
    }

    @Test
    void fleeingFromLeftThreatMovesRightAndFacesLeft() {
        NPCTarget left = new NPCTarget(3, 50, 416, 42, 64);
        controller.update(List.of(left), 0.5);
        controller.update(List.of(left), 0.1);
        npc.advance(NPCStrategyTest.arena(), left, 0.1);
        controller.onAttackResolved(true);
        controller.update(List.of(left), 0.1);
        npc.advance(NPCStrategyTest.arena(), left, 0.1);
        assertEquals(NPCActivity.FLEE, controller.activity());
        assertFalse(npc.state().facingRight());
        assertTrue(npc.state().velocityX() > 0);
    }
}
