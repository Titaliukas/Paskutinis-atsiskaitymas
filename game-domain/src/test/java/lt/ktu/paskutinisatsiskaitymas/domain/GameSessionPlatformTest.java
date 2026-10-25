package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionPlatformTest {
    private final UUID playerId = UUID.randomUUID();

    @Test
    void prototypeFixtureContainsCopiesAboveGround() {
        var platforms = TestArenas.prototypeSession().arena().platforms();

        assertEquals(4, platforms.size());
        assertPlatform(platforms.get(0), GameConstants.CENTER_PLATFORM_X,
                GameConstants.UPPER_PLATFORM_Y, GameConstants.PLATFORM_WIDTH, GameConstants.PLATFORM_HEIGHT);
        assertPlatform(platforms.get(1), GameConstants.LEFT_PLATFORM_X,
                GameConstants.LOWER_PLATFORM_Y, GameConstants.PLATFORM_WIDTH, GameConstants.PLATFORM_HEIGHT);
        assertPlatform(platforms.get(2), GameConstants.RIGHT_PLATFORM_X,
                GameConstants.LOWER_PLATFORM_Y, GameConstants.PLATFORM_WIDTH, GameConstants.PLATFORM_HEIGHT);
        assertPlatform(platforms.get(3), GameConstants.GROUND_X,
                GameConstants.GROUND_Y, GameConstants.GROUND_WIDTH, GameConstants.GROUND_HEIGHT);
        assertEquals(4, platforms.stream().map(Platform::id).distinct().count());
    }

    @Test
    void jumpThroughAndLandOnBothLowerPlatforms() {
        for (int slot = 0; slot < 2; slot++) {
            GameSession session = sessionWithPlayer(slot);
            jumpAndAdvance(session, MovementInput.NONE, 90);

            GameCharacterState landed = state(session);
            assertTrue(landed.grounded());
            assertEquals(GameConstants.LOWER_PLATFORM_Y - GameConstants.PLAYER_HEIGHT,
                    landed.y(), 0.000_001);
        }
    }

    @Test
    void centerPlatformIsReachableFromEitherLowerPlatform() {
        for (int slot = 0; slot < 2; slot++) {
            GameSession session = sessionWithPlayer(slot);
            jumpAndAdvance(session, MovementInput.NONE, 90);

            MovementInput approach = slot == 0
                    ? new MovementInput(false, true, false)
                    : new MovementInput(true, false, false);
            advance(session, approach, slot == 0 ? 16 : 10);
            jumpAndAdvance(session, approach, 40);

            GameCharacterState landed = state(session);
            assertTrue(landed.grounded());
            assertEquals(GameConstants.UPPER_PLATFORM_Y - GameConstants.PLAYER_HEIGHT,
                    landed.y(), 0.000_001);
        }
    }

    @Test
    void walkingOffLowerPlatformFallsBackToGround() {
        GameSession session = sessionWithPlayer(0);
        jumpAndAdvance(session, MovementInput.NONE, 90);
        advance(session, new MovementInput(false, true, false), 80);
        advance(session, MovementInput.NONE, 90);

        GameCharacterState landed = state(session);
        assertTrue(landed.grounded());
        assertEquals(GameConstants.GROUND_Y - GameConstants.PLAYER_HEIGHT,
                landed.y(), 0.000_001);
    }

    private GameSession sessionWithPlayer(int slot) {
        GameSession session = TestArenas.prototypeSession();
        session.addPlayer(new Player(playerId, "Player"), slot, CharacterType.values()[0]);
        return session;
    }

    private void jumpAndAdvance(GameSession session, MovementInput followUp, int ticks) {
        session.advance(Map.of(playerId, new MovementInput(followUp.left(), followUp.right(), true)),
                GameConstants.TICK_SECONDS);
        advance(session, followUp, ticks);
    }

    private void advance(GameSession session, MovementInput input, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            session.advance(Map.of(playerId, input), GameConstants.TICK_SECONDS);
        }
    }

    private GameCharacterState state(GameSession session) {
        return session.characters().getFirst();
    }

    private static void assertPlatform(Platform platform, double x, double y, double width, double height) {
        assertEquals(new Position(x, y), platform.position());
        assertEquals(width, platform.width());
        assertEquals(height, platform.height());
    }
}