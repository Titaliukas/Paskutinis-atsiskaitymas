package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaPhysicsTest {
    private static final double DT = GameConstants.TICK_SECONDS;
    private static final MovementInput LEFT = new MovementInput(true, false, false);
    private static final MovementInput RIGHT = new MovementInput(false, true, false);
    private final ArenaDirector director = new ArenaDirector();
    private final Arena combat = director.construct(new CombatArenaBuilder());
    private final Arena training = director.construct(new TrainingArenaBuilder());

    @Test
    void sessionsSpawnAllSlotsFromArenaAndCharacterRespawnsToItsOwnSavedPosition() {
        for (Arena arena : List.of(combat, training)) {
            GameSession session = new GameSession(UUID.randomUUID(), arena);
            for (int slot = 0; slot < 4; slot++) {
                session.addPlayer(new Player(UUID.randomUUID(), "Player " + slot), slot, CharacterType.GOGIS);
                Position spawn = arena.playerSpawns().get(slot);
                var actual = session.characters().get(slot);
                assertEquals(spawn.x(), actual.x());
                assertEquals(spawn.y(), actual.y());
                GameCharacter character = character(spawn.x(), spawn.y(), true);
                character.advance(RIGHT, arena, DT);
                character.takeDamage(100);
                assertEquals(spawn.x(), character.x());
                assertEquals(spawn.y(), character.y());
                assertTrue(character.state().grounded());
            }
        }
    }

    @Test
    void landsOnEveryCombatSurfaceAndChoosesHighestCrossedSurfaceEvenWithReversedList() {
        var reversed = new ArrayList<>(combat.platforms());
        Collections.reverse(reversed);
        Arena unordered = new Arena(combat.name(), combat.type(), 960, 540, reversed, combat.playerSpawns(),
                combat.npcSpawns(), combat.groundY(), combat.sidePassage());
        for (Platform platform : combat.platforms()) {
            double x = platform.left() + Math.min(80, platform.width() / 2);
            GameCharacter character = character(x, platform.top() - 74, false);
            step(character, unordered, MovementInput.NONE, 15);
            assertEquals(platform.top() - 64, character.y(), 1e-6);
            assertTrue(character.state().grounded());
        }
        GameCharacter falling = character(400, 100, false);
        falling.advance(MovementInput.NONE, unordered, 1);
        assertEquals(215 - 64, falling.y(), 1e-6, "Must hit roof before canopy/ground in one long step");
    }

    @Test
    void defaultBuildClimbsGroundCanopyServiceRoofFromBothSidesInRealSixtyHzSteps() {
        for (boolean fromLeft : new boolean[]{true, false}) {
            Position start = combat.playerSpawns().get(fromLeft ? 0 : 3);
            GameCharacter character = character(start.x(), start.y(), true);
            step(character, combat, fromLeft ? RIGHT : LEFT, fromLeft ? 51 : 106);
            assertStanding(character, 421);
            jump(character, combat, MovementInput.NONE, 40);
            assertStanding(character, 344);
            jump(character, combat, LEFT, 28);
            step(character, combat, MovementInput.NONE, 16);
            assertStanding(character, 280);
            jump(character, combat, MovementInput.NONE, 44);
            assertStanding(character, 215);
            System.out.printf("100/100/100 v2 path from %s: ground -> canopy -> service -> roof, x=%.2f%n",
                    fromLeft ? "left" : "right", character.x());
        }
        // Outside the service platform, the 129-unit canopy-to-roof gap still exceeds the base jump.
        GameCharacter fromCanopy = character(550, 280, true);
        jump(fromCanopy, combat, MovementInput.NONE, 55);
        assertStanding(fromCanopy, 344);
        GameCharacter direct = character(700, 357, true);
        jump(direct, combat, MovementInput.NONE, 60);
        assertStanding(direct, 421);
    }

    @Test
    void bothPassageDirectionsLandInsideAndContinueWithoutBouncingOrResettingState() {
        for (boolean fromLeft : new boolean[]{true, false}) {
            GameCharacter traveler = decoratedCharacter(fromLeft ? 1 : 917);
            GameCharacter control = decoratedCharacter(fromLeft ? 850 : 50);
            UUID id = traveler.id();
            UUID playerId = traveler.playerId();
            MovementInput outward = fromLeft ? LEFT : RIGHT;
            traveler.advance(outward, combat, DT);
            control.advance(outward, combat, DT);
            assertEquals(fromLeft ? 918 : 0, traveler.x(), 1e-6);
            assertStanding(traveler, 421);
            assertEquals(control.state().health(), traveler.state().health());
            assertEquals(control.state().maxHealth(), traveler.state().maxHealth());
            assertEquals(control.state().stressLevel(), traveler.state().stressLevel());
            assertEquals(control.stats().describe(), traveler.stats().describe());
            assertEquals(control.stats().speedMultiplier(), traveler.stats().speedMultiplier());
            assertEquals(control.stats().jumpMultiplier(), traveler.stats().jumpMultiplier());
            assertEquals(control.stats().shielded(), traveler.stats().shielded());
            assertEquals(control.state().slowed(), traveler.state().slowed());
            assertEquals(control.state().characterType(), traveler.state().characterType());
            assertEquals(control.state().velocityX(), traveler.state().velocityX());
            assertEquals(fromLeft ? -1 : 1, traveler.facing());
            assertEquals(id, traveler.id());
            assertEquals(playerId, traveler.playerId());
            assertFalse(traveler.tryUseCooldown(AttackType.RANGED));
            assertFalse(traveler.tryUseCooldown(AttackType.MELEE));
            double exitX = traveler.x();
            traveler.advance(MovementInput.NONE, combat, DT);
            assertEquals(exitX, traveler.x(), "Standing at the exit cannot reteleport");
            traveler.advance(outward, combat, DT);
            assertTrue(fromLeft ? traveler.x() < 918 && traveler.x() > 900
                    : traveler.x() > 0 && traveler.x() < 18);
            assertStanding(traveler, 421);
            traveler.advance(MovementInput.NONE, combat, 5); // Expire shield before lethal damage.
            assertSame(BaseStats.INSTANCE, traveler.stats());
            traveler.takeDamage(1000);
            assertEquals(fromLeft ? 1 : 917, traveler.x(), "Teleport must retain the original respawn");
            assertEquals(357, traveler.y());
        }
    }

    @Test
    void stationaryCharactersAndWrongHeightsAndTrainingRemainClamped() {
        for (Arena arena : List.of(combat, training)) {
            for (double y : new double[]{151, 200, 216, 280, 342, 358}) {
                for (boolean left : new boolean[]{true, false}) {
                    GameCharacter character = character(left ? 0 : 918, y, true);
                    character.advance(left ? LEFT : RIGHT, arena, DT);
                    assertEquals(left ? 0 : 918, character.x());
                }
            }
        }
        for (double x : new double[]{0, 918}) {
            GameCharacter character = character(x, 357, true);
            step(character, combat, MovementInput.NONE, 10);
            assertEquals(x, character.x());
            assertStanding(character, 421);
            GameCharacter other = character(x, 357, true);
            other.advance(x == 0 ? LEFT : RIGHT, training, DT);
            assertEquals(x, other.x());
        }
        GameCharacter onTrainingGround = character(0, training.groundY() - 64, true);
        onTrainingGround.advance(LEFT, training, DT);
        assertEquals(0, onTrainingGround.x());
        GameCharacter knocked = character(0, 357, true);
        knocked.applyImpulse(-100, 0);
        knocked.advance(MovementInput.NONE, combat, DT);
        assertEquals(0, knocked.x(), "Knockback without outward input must not teleport");
    }

    @Test
    void passageAllowsAnAirborneBodyInsideOpeningAndPreservesVerticalMotion() {
        GameCharacter character = character(0, 352, false);
        character.applyImpulse(0, -20);
        character.advance(LEFT, combat, DT);
        assertEquals(918, character.x());
        assertEquals(352 - 20 * DT + 750 * DT * DT, character.y(), 1e-6);
        assertEquals(5, character.state().velocityY(), 1e-6);
        assertFalse(character.state().grounded());
        step(character, combat, MovementInput.NONE, 10);
        assertStanding(character, 421);
    }

    @Test
    void itemsUseSelectedGroundAndStayReachableEvenAtExtremeRandomPositions() {
        for (Arena arena : List.of(combat, training)) {
            for (double fraction : new double[]{0, 0.5, Math.nextDown(1.0)}) {
                GameSession session = new GameSession(UUID.randomUUID(), arena);
                session.spawnItemIfDue(6, new Random(0) {
                    @Override public double nextDouble() { return fraction; }
                });
                Position item = session.activeItems().getFirst().position();
                assertEquals(arena.groundY() - 64, item.y());
                assertTrue(item.x() - 16 >= 0 && item.x() + 16 <= arena.width());
                GameCharacter collector = character(Math.min(918, Math.max(0, item.x() - 21)),
                        arena.groundY() - 64, true);
                assertTrue(collector.overlaps(item, 16));
            }
        }
    }

    private GameCharacter decoratedCharacter(double x) {
        GameCharacter character = new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "Traveler"),
                0, new Position(x, 357), true, CharacterType.GOGIS, new CharacterBuild(90, 110, 100));
        character.takeDamage(7);
        character.applyShield(5);
        character.applySpeedBoost(1.6, 5);
        character.applyJumpBoost(2, 5);
        character.applySlow(0.5, 5);
        character.tryUseCooldown(AttackType.RANGED);
        character.tryUseCooldown(AttackType.MELEE);
        return character;
    }

    private GameCharacter character(double x, double y, boolean grounded) {
        return new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "Player"),
                0, new Position(x, y), grounded, CharacterType.PATASIJUS);
    }

    private void step(GameCharacter character, Arena arena, MovementInput input, int count) {
        for (int i = 0; i < count; i++) character.advance(input, arena, DT);
    }

    private void jump(GameCharacter character, Arena arena, MovementInput direction, int count) {
        character.advance(new MovementInput(direction.left(), direction.right(), true), arena, DT);
        step(character, arena, direction, count - 1);
    }

    private void assertStanding(GameCharacter character, double surfaceY) {
        assertEquals(surfaceY - 64, character.y(), 1e-6);
        assertTrue(character.state().grounded());
        assertEquals(0, character.state().velocityY(), 1e-6);
    }
}
