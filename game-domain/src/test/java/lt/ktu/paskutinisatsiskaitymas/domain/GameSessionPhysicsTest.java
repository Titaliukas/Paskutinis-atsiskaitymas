package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionPhysicsTest {
    private final UUID firstId = UUID.randomUUID();

    @Test
    void movesHorizontallyAndStopsWithoutKeyRepeat() {
        GameSession session = sessionWithFirstPlayer();
        double start = state(session, firstId).x();

        session.advance(Map.of(firstId, new MovementInput(false, true, false)), GameConstants.TICK_SECONDS);
        double moved = state(session, firstId).x();
        assertTrue(moved > start);
        assertEquals(GameConstants.MOVE_SPEED, state(session, firstId).velocityX());

        session.advance(Map.of(), GameConstants.TICK_SECONDS);
        assertEquals(moved, state(session, firstId).x(), 0.000_001);
        assertEquals(0, state(session, firstId).velocityX());
    }

    @Test
    void jumpsOnlyWhileGroundedAndGravityPullsDown() {
        GameSession session = sessionWithFirstPlayer();
        session.advance(Map.of(firstId, new MovementInput(false, false, true)), GameConstants.TICK_SECONDS);
        GameCharacterState afterJump = state(session, firstId);
        assertFalse(afterJump.grounded());
        assertTrue(afterJump.velocityY() < 0);
        assertTrue(afterJump.y() < GameConstants.GROUND_Y - GameConstants.PLAYER_HEIGHT);

        session.advance(Map.of(firstId, new MovementInput(false, false, true)), GameConstants.TICK_SECONDS);
        GameCharacterState airborne = state(session, firstId);
        assertEquals(afterJump.velocityY() + GameConstants.GRAVITY * GameConstants.TICK_SECONDS,
                airborne.velocityY(), 0.000_001, "Airborne jump requests must not reset vertical velocity");
    }

    @Test
    void landsOnGroundAndCollidesWithBothArenaSides() {
        GameSession session = sessionWithFirstPlayer();
        session.advance(Map.of(firstId, new MovementInput(false, false, true)), GameConstants.TICK_SECONDS);
        for (int tick = 0; tick < GameConstants.TICK_RATE * 3; tick++) {
            session.advance(Map.of(), GameConstants.TICK_SECONDS);
        }
        GameCharacterState landed = state(session, firstId);
        assertTrue(landed.grounded());
        assertEquals(GameConstants.GROUND_Y - GameConstants.PLAYER_HEIGHT, landed.y(), 0.000_001);
        assertEquals(0, landed.velocityY(), 0.000_001);

        for (int tick = 0; tick < GameConstants.TICK_RATE * 2; tick++) {
            session.advance(Map.of(firstId, new MovementInput(true, false, false)), GameConstants.TICK_SECONDS);
        }
        assertEquals(0, state(session, firstId).x(), 0.000_001);

        for (int tick = 0; tick < GameConstants.TICK_RATE * 5; tick++) {
            session.advance(Map.of(firstId, new MovementInput(false, true, false)), GameConstants.TICK_SECONDS);
        }
        assertEquals(GameConstants.ARENA_WIDTH - GameConstants.PLAYER_WIDTH,
                state(session, firstId).x(), 0.000_001);
    }

    @Test
    void updatesTwoPlayersIndependently() {
        GameSession session = sessionWithFirstPlayer();
        UUID secondId = UUID.randomUUID();
        session.addPlayer(new Player(secondId, "Second"), 1);
        double firstStart = state(session, firstId).x();
        double secondStart = state(session, secondId).x();

        session.advance(Map.of(firstId, new MovementInput(false, true, false)), GameConstants.TICK_SECONDS);

        assertTrue(state(session, firstId).x() > firstStart);
        assertEquals(secondStart, state(session, secondId).x(), 0.000_001);
    }

    private GameSession sessionWithFirstPlayer() {
        GameSession session = GameSession.createDefault();
        session.addPlayer(new Player(firstId, "First"), 0);
        return session;
    }

    private static GameCharacterState state(GameSession session, UUID playerId) {
        return session.characters().stream()
                .filter(character -> character.playerId().equals(playerId))
                .findFirst().orElseThrow();
    }
}
