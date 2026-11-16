package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovingPlatformPhysicsTest {
    private static final double DT = GameConstants.TICK_SECONDS;

    @Test
    void carriesFourPlayersAcrossReversalsOnBothAxes() {
        for (boolean horizontal : new boolean[]{true, false}) {
            GameSession session = session(horizontal);
            for (int slot = 0; slot < 4; slot++) {
                session.addPlayer(new Player(UUID.randomUUID(), "Rider " + slot), slot, CharacterType.GOGIS);
            }
            for (int tick = 0; tick < 600; tick++) {
                session.advance(Map.of(), DT);
                Platform platform = session.arena().platforms().getFirst();
                for (GameCharacterState state : session.characters()) {
                    assertEquals(platform.left() + 20 + state.slot() * 50, state.x(), 1e-6);
                    assertEquals(platform.top() - 64, state.y(), 1e-6);
                    assertTrue(state.grounded());
                    assertEquals(0, state.velocityY(), 1e-6);
                }
            }
        }
    }

    @Test
    void jumpingDetachesImmediatelyWithoutInheritingPlatformMomentum() {
        for (boolean horizontal : new boolean[]{true, false}) {
            GameSession session = session(horizontal);
            UUID id = UUID.randomUUID();
            session.addPlayer(new Player(id, "Jumper"), 0, CharacterType.GOGIS);
            session.advance(Map.of(id, new MovementInput(false, false, true)), DT);
            GameCharacterState jumped = session.characters().getFirst();
            assertFalse(jumped.grounded());
            assertEquals(120, jumped.x(), 1e-6);
            assertEquals(286 - 600 * DT + 750 * DT * DT, jumped.y(), 1e-6);
            for (int i = 0; i < 10; i++) session.advance(Map.of(), DT);
            assertEquals(120, session.characters().getFirst().x(), 1e-6);
        }
    }

    @Test
    void walkingOffFallsToGroundAndStopsReceivingPlatformMotion() {
        GameSession session = session(true);
        UUID id = UUID.randomUUID();
        session.addPlayer(new Player(id, "Walker"), 0, CharacterType.GOGIS);
        for (int i = 0; i < 100; i++) session.advance(Map.of(id, new MovementInput(false, true, false)), DT);
        for (int i = 0; i < 90; i++) session.advance(Map.of(), DT);
        GameCharacterState landed = session.characters().getFirst();
        assertEquals(416, landed.y(), 1e-6);
        assertTrue(landed.grounded());
        session.advance(Map.of(), DT);
        assertEquals(landed.x(), session.characters().getFirst().x(), 1e-6);
    }

    @Test
    void ascendingPlatformCatchesFallingPlayerButAllowsJumpingFromBelow() {
        Platform platform = new VerticalPlatform(UUID.randomUUID(), new Position(100, 350), 300, 10, 250, 60);
        Arena arena = arena(List.of(platform));
        GameCharacter falling = character(new Position(120, 285), false);
        platform.update(0.1);
        falling.advance(MovementInput.NONE, arena, 0.1);
        assertEquals(280, falling.y(), 1e-6);
        assertTrue(falling.state().grounded());

        GameCharacter jumping = character(new Position(120, 350), false);
        jumping.applyImpulse(0, -600);
        jumping.advance(MovementInput.NONE, arena, 0.1);
        assertFalse(jumping.state().grounded());
        assertEquals(297.5, jumping.y(), 1e-6);
    }

    @Test
    void stationaryAndMovingPlatformsChooseHighestCrossedTopRegardlessOfOrder() {
        Platform moving = new VerticalPlatform(UUID.randomUUID(), new Position(100, 350), 300, 10, 250, 60);
        Platform fixed = new StationaryPlatform(UUID.randomUUID(), new Position(100, 400), 300, 10);
        moving.update(0.1);
        for (List<Platform> surfaces : List.of(List.of(moving, fixed), List.of(fixed, moving))) {
            GameCharacter falling = character(new Position(120, 200), false);
            falling.advance(MovementInput.NONE, arena(surfaces), 0.5);
            assertEquals(280, falling.y(), 1e-6);
        }
    }

    @Test
    void ambiguousSupportCarriesOnlyOnceUsingArenaOrder() {
        Platform first = horizontal();
        Platform second = new HorizontalPlatform(UUID.randomUUID(), new Position(100, 350), 300, 10, 300, 120);
        GameSession session = new GameSession(UUID.randomUUID(), arena(List.of(first, second)));
        session.addPlayer(new Player(UUID.randomUUID(), "Rider"), 0, CharacterType.GOGIS);
        session.advance(Map.of(), DT);
        assertEquals(121, session.characters().getFirst().x(), 1e-6);
    }

    @Test
    void knockbackAndRespawnReleaseOldSupport() {
        Platform platform = horizontal();
        Arena arena = arena(List.of(platform));
        GameCharacter character = character(new Position(700, 416), true);
        character.moveWithPlatform(-580, -130);
        assertTrue(platform.supports(character));
        character.applyImpulse(100, -100);
        assertFalse(platform.supports(character));
        character.takeDamage(1000);
        assertEquals(700, character.x());
        assertEquals(416, character.y());
        assertFalse(platform.supports(character));
        platform.update(DT);
        character.advance(MovementInput.NONE, arena, DT);
        assertEquals(700, character.x());
    }

    @Test
    void sessionCarriesNPCsAndRemovalDoesNotLeavePassengersBehind() {
        for (boolean horizontal : new boolean[]{true, false}) {
            GameSession session = session(horizontal);
            session.spawnNPC(1, new Position(260, 286), 220, 350);
            for (int tick = 0; tick < 600; tick++) {
                session.advance(Map.of(), DT);
                NPCState npc = session.npcs().getFirst().state();
                Platform platform = session.arena().platforms().getFirst();
                assertTrue(npc.grounded(), "horizontal=" + horizontal + " tick=" + tick
                        + " npc=" + npc + " platform=" + platform.position());
                assertEquals(platform.top() - 64, npc.preciseY(), 1e-6);
                assertEquals(Math.round(npc.preciseX()), npc.x().longValue());
            }
            session.removeNPC(1);
            session.advance(Map.of(), DT);
            assertTrue(session.npcs().isEmpty());
        }
    }

    @Test
    void sessionMotionIsIndependentOfRecipeAndOtherSessions() {
        Arena recipe = arena(List.of(horizontal()));
        GameSession first = new GameSession(UUID.randomUUID(), recipe);
        GameSession second = new GameSession(UUID.randomUUID(), recipe);
        first.advance(Map.of(), 0.5);
        assertEquals(130, first.arena().platforms().getFirst().left());
        assertEquals(100, second.arena().platforms().getFirst().left());
        assertEquals(100, recipe.platforms().getFirst().left());
        assertNotEquals(first.arena().platforms().getFirst().id(), second.arena().platforms().getFirst().id());
    }

    private GameSession session(boolean horizontal) {
        return new GameSession(UUID.randomUUID(), arena(List.of(horizontal ? horizontal()
                : new VerticalPlatform(UUID.randomUUID(), new Position(100, 350), 300, 10, 250, 40))));
    }

    private Platform horizontal() {
        return new HorizontalPlatform(UUID.randomUUID(), new Position(100, 350), 300, 10, 200, 60);
    }

    private Arena arena(List<Platform> elevated) {
        var platforms = new java.util.ArrayList<>(elevated);
        platforms.add(new StationaryPlatform(UUID.randomUUID(), new Position(0, 480), 960, 60));
        return new Arena("Moving test", ArenaType.TRAINING, 960, 540, platforms,
                List.of(new Position(120, 286), new Position(170, 286), new Position(220, 286),
                        new Position(270, 286)), List.of(), 480, Optional.empty());
    }

    private GameCharacter character(Position spawn, boolean grounded) {
        return new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "Test"), 0,
                spawn, grounded, CharacterType.GOGIS);
    }
}
