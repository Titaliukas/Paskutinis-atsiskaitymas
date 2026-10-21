package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterBuildTest {
    @Test
    void validatesRangesStepsAndBudgetWhileAllowingUnusedPoints() {
        assertEquals(100, CharacterBuild.DEFAULT.maxHealth());
        assertDoesNotThrow(() -> new CharacterBuild(50, 50, 50));
        assertDoesNotThrow(() -> new CharacterBuild(150, 100, 50));
        for (int invalid : new int[]{Integer.MIN_VALUE, 45, 51, 151, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CharacterBuild(invalid, 100, 100));
            assertThrows(IllegalArgumentException.class, () -> new CharacterBuild(100, invalid, 100));
            assertThrows(IllegalArgumentException.class, () -> new CharacterBuild(100, 100, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new CharacterBuild(105, 100, 100));
    }

    @Test
    void appliesPermanentStatsToPhysicsAndComposesWithTimedEffects() {
        GameCharacter character = character(new CharacterBuild(50, 125, 125));
        assertEquals(50, character.state().health());
        assertEquals(50, character.state().maxHealth());
        character.applySpeedBoost(1.6, 8);
        character.applySlow(0.5, 8);
        character.applyJumpBoost(2, 8);
        character.advance(new MovementInput(false, true, true), NPCStrategyTest.arena(), 0.01);
        assertEquals(GameConstants.MOVE_SPEED * 1.25 * 1.6 * 0.5, character.state().velocityX(), 1e-9);
        assertEquals(-GameConstants.JUMP_SPEED * Math.sqrt(1.25 * 2) + GameConstants.GRAVITY * 0.01,
                character.state().velocityY(), 1e-9);
    }

    @Test
    void respawnRestoresCustomHealthAndKeepsBuildAfterEffectsExpireOrClear() {
        GameCharacter character = character(new CharacterBuild(125, 125, 50));
        character.applySpeedBoost(1.6, 8);
        character.applyJumpBoost(2, 8);
        assertTrue(character.takeDamage(25));
        assertEquals(100, character.state().health());
        assertTrue(character.takeDamage(100));
        assertEquals(125, character.state().health());
        assertEquals(125, character.state().maxHealth());
        assertSame(BaseStats.INSTANCE, character.stats());
        character.advance(new MovementInput(false, true, true), NPCStrategyTest.arena(), 0.01);
        assertEquals(GameConstants.MOVE_SPEED * 1.25, character.state().velocityX(), 1e-9);
        assertEquals(-GameConstants.JUMP_SPEED * Math.sqrt(0.5) + GameConstants.GRAVITY * 0.01,
                character.state().velocityY(), 1e-9);
        character.applySpeedBoost(1.6, 0.005);
        character.advance(new MovementInput(false, true, false), NPCStrategyTest.arena(), 0.01);
        assertEquals(GameConstants.MOVE_SPEED * 1.25, character.state().velocityX(), 1e-9);
    }

    @Test
    void defaultsRemainIdenticalForEveryCharacter() {
        for (CharacterType type : CharacterType.values()) {
            GameSession session = GameSession.createDefault();
            session.addPlayer(new Player(UUID.randomUUID(), "Player"), 0, type);
            assertEquals(GameConstants.MAX_HEALTH, session.characters().getFirst().maxHealth());
        }
    }

    private GameCharacter character(CharacterBuild build) {
        return new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "Player"),
                0, new Position(180, 416), true, CharacterType.PATASIJUS, build);
    }
}
