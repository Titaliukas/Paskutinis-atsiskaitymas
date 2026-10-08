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

    @Test
    void fourSpawnsAreDistinctClearOfNPCsAndSupportIndependentInputs() {
        GameSession session = GameSession.createDefault();
        UUID[] ids = new UUID[4];
        for (int slot = 0; slot < 4; slot++) {
            ids[slot] = UUID.randomUUID();
            session.addPlayer(new Player(ids[slot], "Player " + slot), slot);
        }
        assertEquals(GameConstants.FIRST_SPAWN_X, state(session, ids[0]).x());
        assertEquals(GameConstants.SECOND_SPAWN_X, state(session, ids[1]).x());
        var states = session.characters();
        assertEquals(4, states.stream().map(GameCharacterState::x).distinct().count());
        for (GameCharacterState character : states) {
            assertTrue(character.x() >= 0 && character.x() + GameConstants.PLAYER_WIDTH <= session.arena().width());
            for (GameCharacterState other : states) {
                if (!other.playerId().equals(character.playerId())) {
                    assertTrue(character.x() + GameConstants.PLAYER_WIDTH <= other.x()
                            || other.x() + GameConstants.PLAYER_WIDTH <= character.x());
                }
            }
            for (NPCView npc : session.npcs()) {
                assertTrue(character.x() + GameConstants.PLAYER_WIDTH <= npc.state().preciseX()
                        || npc.state().preciseX() + npc.state().width() <= character.x());
            }
        }
        assertThrows(IllegalStateException.class, () -> session.addPlayer(new Player(UUID.randomUUID(), "Fifth"), 0));
        session.advance(Map.of(ids[0], new MovementInput(false, true, false),
                ids[1], new MovementInput(true, false, false), ids[2], new MovementInput(false, false, true)),
                GameConstants.TICK_SECONDS);
        assertTrue(state(session, ids[0]).x() > states.get(0).x());
        assertTrue(state(session, ids[1]).x() < states.get(1).x());
        assertTrue(state(session, ids[2]).y() < states.get(2).y());
        assertEquals(states.get(3).x(), state(session, ids[3]).x());
        assertEquals(states.get(3).y(), state(session, ids[3]).y());
        for (int invalid : new int[]{-1, 4}) {
            assertThrows(IllegalArgumentException.class, () -> GameConstants.spawnX(invalid));
            GameSession empty = GameSession.createDefault();
            assertThrows(IllegalArgumentException.class, () -> empty.addPlayer(new Player(UUID.randomUUID(), "Invalid"), invalid));
        }
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
