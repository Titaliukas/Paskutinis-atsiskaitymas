package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaBuilderTest {
    private final ArenaDirector director = new ArenaDirector();

    @Test
    void directorUsesTheSameOrderedStepsWithoutKnowingConcreteRecipes() {
        List<String> steps = new ArrayList<>();
        Arena expected = director.construct(new TrainingArenaBuilder());
        IArenaBuilder recording = new IArenaBuilder() {
            public IArenaBuilder reset() { steps.add("reset"); return this; }
            public IArenaBuilder buildBounds() { steps.add("bounds"); return this; }
            public IArenaBuilder buildPlatforms() { steps.add("platforms"); return this; }
            public IArenaBuilder buildPlayerSpawns() { steps.add("players"); return this; }
            public IArenaBuilder buildNPCSpawns() { steps.add("NPCs"); return this; }
            public Arena build() { steps.add("build"); return expected; }
        };
        assertSame(expected, director.construct(recording));
        assertEquals(List.of("reset", "bounds", "platforms", "players", "NPCs", "build"), steps);
    }

    @Test
    void buildsBothProductsAndReusesBuildersWithoutMutatingEarlierProducts() {
        for (IArenaBuilder builder : List.of(new CombatArenaBuilder(), new TrainingArenaBuilder())) {
            Arena first = director.construct(builder);
            var platformIds = first.platforms().stream().map(Platform::id).toList();
            assertSame(builder, builder.reset());
            assertThrows(IllegalArgumentException.class, builder::build, "reset must discard bounds/spawns too");
            Arena second = director.construct(builder);
            assertEquals(first.type(), second.type());
            assertEquals(first.playerSpawns(), second.playerSpawns());
            assertEquals(first.npcSpawns(), second.npcSpawns());
            assertEquals(first.platforms().size(), second.platforms().size());
            assertEquals(platformIds, first.platforms().stream().map(Platform::id).toList());
            assertTrue(second.platforms().stream().noneMatch(p -> platformIds.contains(p.id())));
            assertThrows(UnsupportedOperationException.class, first.platforms()::clear);
            assertThrows(UnsupportedOperationException.class, first.playerSpawns()::clear);
            assertThrows(UnsupportedOperationException.class, first.npcSpawns()::clear);
        }
    }

    @Test
    void trainingHasOnlyGroundFourSafeSpawnsAndNoNPCsOrPassage() {
        Arena training = director.construct(new TrainingArenaBuilder());
        assertEquals(ArenaType.TRAINING, training.type());
        assertEquals(960, training.width());
        assertEquals(540, training.height());
        assertEquals(1, training.platforms().size());
        assertEquals(new Position(0, 480), training.platforms().getFirst().position());
        assertEquals(960, training.platforms().getFirst().width());
        assertTrue(training.npcSpawns().isEmpty());
        assertTrue(training.sidePassage().isEmpty());
        assertSpawnsSafe(training);
    }

    @Test
    void combatHasExactlyFiveMeasuredSurfacesAndTwoNPCsOnRoofAndGround() {
        Arena arena = director.construct(new CombatArenaBuilder());
        assertEquals(ArenaType.COMBAT, arena.type());
        double[][] geometry = {{52, 222, 857, 6}, {339, 309, 281, 6},
            {0, 351, 183, 6}, {785, 351, 175, 6}, {0, 467, 960, 73}};
        assertEquals(geometry.length, arena.platforms().size());
        for (int i = 0; i < geometry.length; i++) {
            Platform p = arena.platforms().get(i);
            assertArrayEquals(geometry[i], new double[]{p.left(), p.top(), p.width(), p.height()});
        }
        assertEquals(5, arena.platforms().stream().map(Platform::id).distinct().count());
        assertEquals(2, arena.npcSpawns().size());
        assertEquals(158, arena.npcSpawns().get(0).position().y());
        assertEquals(403, arena.npcSpawns().get(1).position().y());
        assertEquals(new SidePassage(275, 351), arena.sidePassage().orElseThrow());
        assertSpawnsSafe(arena);
        for (NPCSpawn spawn : arena.npcSpawns()) {
            Platform support = arena.platforms().stream()
                    .filter(p -> p.top() == spawn.position().y() + 64).findFirst().orElseThrow();
            assertTrue(spawn.leftBoundary() >= support.left());
            assertTrue(spawn.rightBoundary() + 42 <= support.right());
        }
    }

    @Test
    void arenaCopiesCallerCollectionsAndSessionsOwnIndependentNPCsAndControllers() {
        Arena combat = director.construct(new CombatArenaBuilder());
        var platforms = new ArrayList<>(combat.platforms());
        var players = new ArrayList<>(combat.playerSpawns());
        var npcs = new ArrayList<>(combat.npcSpawns());
        Arena detached = new Arena("Renamed", combat.type(), 960, 540, platforms, players, npcs,
                combat.groundY(), combat.sidePassage());
        platforms.clear(); players.clear(); npcs.clear();
        assertEquals(5, detached.platforms().size());
        assertEquals(4, detached.playerSpawns().size());
        assertEquals(2, detached.npcSpawns().size());
        GameSession first = new GameSession(UUID.randomUUID(), detached);
        GameSession second = new GameSession(UUID.randomUUID(), detached);
        var before = second.npcs();
        for (int i = 0; i < 300; i++) {
            first.advance(Map.of(), GameConstants.TICK_SECONDS);
            var roof = first.npcs().getFirst().state();
            assertEquals(158, roof.preciseY(), 1e-6);
            assertTrue(roof.preciseX() >= 52 && roof.preciseX() + 42 <= 909);
        }
        first.removeNPC(1);
        assertEquals(before, second.npcs());
        assertEquals(List.of(1, 2), second.npcControllerIds());
        assertEquals(2, detached.npcSpawns().size());
    }

    private void assertSpawnsSafe(Arena arena) {
        assertEquals(4, arena.playerSpawns().size());
        for (int i = 0; i < 4; i++) {
            Position p = arena.playerSpawns().get(i);
            assertEquals(arena.groundY() - 64, p.y());
            assertTrue(p.x() >= 0 && p.x() + 42 <= arena.width());
            for (int j = 0; j < i; j++) {
                assertTrue(Math.abs(p.x() - arena.playerSpawns().get(j).x()) >= 42);
            }
            for (NPCSpawn npc : arena.npcSpawns()) {
                assertTrue(Math.abs(p.x() - npc.position().x()) >= 42
                        || Math.abs(p.y() - npc.position().y()) >= 64);
            }
        }
    }
}
