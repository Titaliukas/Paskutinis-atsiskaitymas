package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.ArenaType;
import lt.ktu.paskutinisatsiskaitymas.domain.CharacterType;
import lt.ktu.paskutinisatsiskaitymas.domain.GameSession;
import lt.ktu.paskutinisatsiskaitymas.domain.Player;
import lt.ktu.paskutinisatsiskaitymas.protocol.JsonMessageCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaSelectionTest {
    @Test
    void movingGeometrySurvivesJSONAndEarlierSnapshotsRemainDetached() throws Exception {
        var codec = new JsonMessageCodec();
        for (String type : List.of("combat", "training")) {
            GameSession session = new GameSession(UUID.randomUUID(), ArenaSelection.create(type, null));
            var before = SnapshotMapper.map(0, session);
            session.advance(Map.of(), 0.5);
            var after = SnapshotMapper.map(1, session);
            int horizontalIndex = type.equals("combat") ? 4 : 1;
            var horizontalBefore = before.arena().platforms().get(horizontalIndex);
            var horizontalAfter = after.arena().platforms().get(horizontalIndex);
            var verticalBefore = before.arena().platforms().get(horizontalIndex + 1);
            var verticalAfter = after.arena().platforms().get(horizontalIndex + 1);
            assertEquals(30, horizontalAfter.x() - horizontalBefore.x(), 1e-6);
            assertEquals(horizontalBefore.y(), horizontalAfter.y());
            assertEquals(-20, verticalAfter.y() - verticalBefore.y(), 1e-6);
            assertEquals(verticalBefore.x(), verticalAfter.x());
            assertEquals("service", horizontalAfter.kind());
            assertEquals(before, codec.decode(codec.encode(before)));
            assertEquals(after, codec.decode(codec.encode(after)));
            assertEquals(before.arena().platforms().subList(0, horizontalIndex),
                    after.arena().platforms().subList(0, horizontalIndex));
        }
    }

    @Test
    void selectsBothBuildersWithPropertyThenEnvironmentThenCombatDefault() {
        assertEquals(ArenaType.COMBAT, ArenaSelection.create(null, null).type());
        assertEquals(ArenaType.TRAINING, ArenaSelection.create(null, "training").type());
        assertEquals(ArenaType.COMBAT, ArenaSelection.create(null, "combat").type());
        assertEquals(ArenaType.TRAINING, ArenaSelection.create("training", "combat").type());
        assertEquals(ArenaType.COMBAT, ArenaSelection.create("combat", "training").type());
        for (String bad : new String[]{"", "COMBAT", " training", "arena.png", "unknown"}) {
            var error = assertThrows(IllegalArgumentException.class, () -> ArenaSelection.create(bad, "combat"));
            assertTrue(error.getMessage().contains("game.arena / GAME_ARENA"));
            assertThrows(IllegalArgumentException.class, () -> ArenaSelection.create(null, bad));
        }
        assertEquals(ArenaType.COMBAT, ArenaSelection.create("combat", "invalid").type());
    }

    @Test
    void mapsBothRealArenaIdentitiesGeometrySpawnsAndNPCsThroughJSON() throws Exception {
        var codec = new JsonMessageCodec();
        for (String type : List.of("combat", "training")) {
            GameSession session = new GameSession(UUID.randomUUID(), ArenaSelection.create(type, null));
            for (int i = 0; i < 4; i++) {
                session.addPlayer(new Player(UUID.randomUUID(), "Player " + i), i, CharacterType.values()[i]);
            }
            var snapshot = SnapshotMapper.map(0, session);
            assertEquals(type, snapshot.arena().arenaType());
            assertEquals(session.arena().platforms().size(), snapshot.arena().platforms().size());
            assertEquals(session.arena().npcSpawns().size(), snapshot.npcs().size());
            assertEquals(4, snapshot.players().size());
            if (type.equals("combat")) {
                var passage = snapshot.arena().sidePassage();
                assertNotNull(passage);
                assertEquals(session.arena().sidePassage().orElseThrow().openingTop(), passage.openingTop());
                assertEquals(session.arena().groundY(), passage.supportY());
                assertEquals("service", snapshot.arena().platforms().get(2).kind());
            } else {
                assertNull(snapshot.arena().sidePassage());
            }
            assertEquals(snapshot, codec.decode(codec.encode(snapshot)));
        }
    }
}
