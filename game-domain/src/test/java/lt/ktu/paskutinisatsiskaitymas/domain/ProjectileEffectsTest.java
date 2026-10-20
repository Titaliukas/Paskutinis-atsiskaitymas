package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileEffectsTest {
    private GameCharacter player() {
        return new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "Player"),
                0, new Position(180, 416), true, CharacterType.PATASIJUS);
    }

    @Test
    void slowRefreshesWithoutStackingAndExpiresAfterThreeSeconds() {
        GameCharacter player = player();
        MovementInput right = new MovementInput(false, true, false);
        player.applyProjectileSlow();
        player.advance(right, NPCStrategyTest.arena(), 0.1);
        assertEquals(247, player.state().velocityX(), 1e-9);
        player.advance(MovementInput.NONE, NPCStrategyTest.arena(), 2);
        player.applyProjectileSlow();
        player.advance(right, NPCStrategyTest.arena(), 0.1);
        assertEquals(247, player.state().velocityX(), 1e-9);
        player.advance(MovementInput.NONE, NPCStrategyTest.arena(), 2.8);
        assertTrue(player.state().slowed());
        player.advance(right, NPCStrategyTest.arena(), 0.1);
        assertFalse(player.state().slowed());
        assertEquals(260, player.state().velocityX(), 1e-9);
        assertEquals(0, player.state().stressLevel());
    }

    @Test
    void shieldBlocksNewSlowAndRefreshButDoesNotCleanseExistingSlow() {
        GameCharacter player = player();
        player.applyShield(8);
        player.applyProjectileSlow();
        assertFalse(player.state().slowed());
        player = player();
        player.applyProjectileSlow();
        player.advance(MovementInput.NONE, NPCStrategyTest.arena(), 2);
        player.applyShield(8);
        player.applyProjectileSlow();
        assertTrue(player.state().slowed());
        player.advance(MovementInput.NONE, NPCStrategyTest.arena(), 1);
        assertFalse(player.state().slowed(), "Blocked hit must not refresh existing slow");
    }

    @Test
    void slowMultipliesSpeedBoostIndependentlyWithoutChangingJumpOrStress() {
        GameCharacter player = player();
        player.applySpeedBoost(1.6, 8);
        player.applyJumpBoost(2, 8);
        player.applyProjectileSlow();
        player.advance(new MovementInput(false, true, true), NPCStrategyTest.arena(), 0.01);
        assertEquals(260 * 1.6 * 0.95, player.state().velocityX(), 1e-9);
        assertEquals(-600 * Math.sqrt(2) + 15, player.state().velocityY(), 1e-9);
        assertEquals(0, player.state().stressLevel());
        player.advance(new MovementInput(false, true, false), NPCStrategyTest.arena(), 2.99);
        assertFalse(player.state().slowed());
        assertEquals(260 * 1.6, player.state().velocityX(), 1e-9);
    }

    @Test
    void stunPausesControllerAndMotionWhileGravityAndBothCooldownsContinue() {
        NPC npc = new NPC(1, new Position(100, 100), 42, 64, 10, 18, 10, 1.2,
                new PatrolStrategy(0, 900));
        NPCBehaviorController controller = new NPCBehaviorController(npc, new PatrolStrategy(0, 900),
                200, 0.5, 150, 0.5, 0.8);
        NPCTarget target = new NPCTarget(7, 180, 100, 42, 64);
        controller.update(List.of(target), 0.5);
        npc.recordAttackAttempt();
        npc.recordThrowAttempt();
        npc.stun();
        for (int i = 0; i < 30; i++) {
            controller.update(List.of(target), 1.0 / 60);
            NPCAction action = npc.advance(NPCStrategyTest.arena(), target, 1.0 / 60);
            assertFalse(action.hasAttackRequest());
            assertFalse(action.hasThrowRequest());
        }
        assertTrue(npc.state().stunned());
        assertEquals(100, npc.state().preciseX());
        assertTrue(npc.state().preciseY() > 100);
        npc.stun(); // Refresh rather than stack.
        for (int i = 0; i < 60; i++) {
            controller.update(List.of(target), 1.0 / 60);
            npc.advance(NPCStrategyTest.arena(), target, 1.0 / 60);
        }
        assertFalse(npc.state().stunned());
        assertTrue(npc.state().attackReady());
        assertTrue(npc.state().throwReady());
        assertEquals(NPCActivity.CHASE, controller.activity(), "Chase timeout was paused");
        controller.update(List.of(target), 0.1);
        assertEquals(NPCActivity.CHASE, controller.activity());
        assertTrue(npc.advance(NPCStrategyTest.arena(), target, 0.1).hasThrowRequest());
        assertTrue(npc.state().preciseX() > 100);
    }
}
