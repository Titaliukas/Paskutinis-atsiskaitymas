package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.List;
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
