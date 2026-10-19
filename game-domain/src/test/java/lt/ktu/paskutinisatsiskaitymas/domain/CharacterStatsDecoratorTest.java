package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterStatsDecoratorTest {
    private static final double EPSILON = 1e-9;

    private static GameCharacter character() {
        return new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "Player"),
                0, new Position(180, 416), true, CharacterType.PATASIJUS);
    }

    @Test
    void baseStatsAreUnmodified() {
        CharacterStats base = BaseStats.INSTANCE;
        assertEquals(1.0, base.speedMultiplier(), EPSILON);
        assertEquals(1.0, base.jumpMultiplier(), EPSILON);
        assertFalse(base.shielded());
        assertEquals("Base", base.describe());
    }

    @Test
    void threeDecorationLevelsCombineEveryLayer() {
        CharacterStats stats = new ShieldDecorator(
                new JumpBoostDecorator(
                        new SpeedBoostDecorator(BaseStats.INSTANCE, 1.6, 8), 2.0, 8), 8);

        assertEquals("Shield(JumpBoost(SpeedBoost(Base)))", stats.describe());
        assertEquals(1.6, stats.speedMultiplier(), EPSILON);
        assertEquals(2.0, stats.jumpMultiplier(), EPSILON);
        assertTrue(stats.shielded());
    }

    @Test
    void fourLevelsStackBoostAndSlowInsteadOfOverwriting() {
        GameCharacter character = character();
        character.applySpeedBoost(1.6, 8);
        character.applyJumpBoost(2.0, 8);
        character.applyShield(8);
        character.applySlow(0.3, 4);

        CharacterStats stats = character.stats();
        assertEquals("Slow(Shield(JumpBoost(SpeedBoost(Base))))", stats.describe());
        assertEquals(1.6 * 0.3, stats.speedMultiplier(), EPSILON);
        assertEquals(2.0, stats.jumpMultiplier(), EPSILON);
        assertTrue(character.shielded());
    }

    @Test
    void decoratedSpeedDrivesMovement() {
        GameCharacter character = character();
        character.applySpeedBoost(1.6, 8);
        character.applySlow(0.5, 8);
        character.advance(new MovementInput(false, true, false), NPCStrategyTest.arena(), 0.01);
        assertEquals(GameConstants.MOVE_SPEED * 1.6 * 0.5, character.state().velocityX(), EPSILON);
    }

    @Test
    void expiredLayersRemoveThemselvesAndOthersStay() {
        CharacterStats stats = new SlowDecorator(
                new ShieldDecorator(new SpeedBoostDecorator(BaseStats.INSTANCE, 1.6, 8), 2), 0.5, 4);

        stats = stats.tick(3);
        assertEquals("Slow(SpeedBoost(Base))", stats.describe());
        assertFalse(stats.shielded());

        stats = stats.tick(1.5);
        assertEquals("SpeedBoost(Base)", stats.describe());
        assertEquals(1.6, stats.speedMultiplier(), EPSILON);

        stats = stats.tick(4);
        assertSame(BaseStats.INSTANCE, stats);
    }

    @Test
    void reapplyingTheSameEffectRefreshesInsteadOfStacking() {
        GameCharacter character = character();
        character.applySlow(0.3, 4);
        character.applyShield(8);
        character.advance(MovementInput.NONE, NPCStrategyTest.arena(), 3);
        character.applySlow(0.3, 4);

        assertEquals("Slow(Shield(Base))", character.stats().describe());
        assertEquals(0.3, character.stats().speedMultiplier(), EPSILON);

        // The first slow would have expired by now; the refreshed one is still running.
        character.advance(MovementInput.NONE, NPCStrategyTest.arena(), 3.5);
        assertEquals(0.3, character.stats().speedMultiplier(), EPSILON);
    }

    @Test
    void shieldLayerBlocksDamage() {
        GameCharacter character = character();
        character.applyShield(8);
        assertFalse(character.takeDamage(10));
        assertEquals(GameConstants.MAX_HEALTH, character.state().health());
    }

    @Test
    void respawnClearsEveryLayer() {
        GameCharacter character = character();
        character.applySpeedBoost(1.6, 8);
        character.applyJumpBoost(2.0, 8);
        character.applySlow(0.3, 4);
        assertTrue(character.takeDamage(GameConstants.MAX_HEALTH));
        assertSame(BaseStats.INSTANCE, character.stats());
    }

    @Test
    void rejectsInvalidEffects() {
        assertThrows(IllegalArgumentException.class, () -> new SlowDecorator(BaseStats.INSTANCE, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> new ShieldDecorator(BaseStats.INSTANCE, 0));
        assertThrows(NullPointerException.class, () -> new ShieldDecorator(null, 4));
    }
}
