package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NPCSnapshotTest {
    private final JsonMessageCodec codec = new JsonMessageCodec();

    private WorldSnapshot world(NPCSnapshot npc) {
        return new WorldSnapshot(7, new ArenaSnapshot(960, 540, List.of()), List.of(), List.of(), List.of(npc));
    }

    @Test
    void roundTripsAllActivitiesPrecisePositionFacingAndSequence() throws Exception {
        for (String activity : List.of("PATROL", "CHASE", "ATTACK", "FLEE")) {
            WorldSnapshot snapshot = world(new NPCSnapshot(1, 123.456, 416.25, 42, 64, activity, false, 17));
            String json = codec.encode(snapshot);
            assertFalse(json.contains("lt.ktu"));
            assertEquals(snapshot, codec.decode(json));
        }
    }

    @Test
    void rejectsInvalidNPCFieldsAndMalformedWireData() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new NPCSnapshot(0, 0, 0, 42, 64, "PATROL", true, 0));
        assertThrows(IllegalArgumentException.class, () -> new NPCSnapshot(1, Double.NaN, 0, 42, 64, "PATROL", true, 0));
        assertThrows(IllegalArgumentException.class, () -> new NPCSnapshot(1, 0, 0, 0, 64, "PATROL", true, 0));
        assertThrows(IllegalArgumentException.class, () -> new NPCSnapshot(1, 0, 0, 42, 64, "WALK", true, 0));
        assertThrows(IllegalArgumentException.class, () -> new NPCSnapshot(1, 0, 0, 42, 64, "PATROL", true, -1));
        NPCSnapshot npc = new NPCSnapshot(1, 10, 20, 42, 64, "PATROL", true, 0);
        assertThrows(IllegalArgumentException.class, () -> new WorldSnapshot(0,
                new ArenaSnapshot(960, 540, List.of()), List.of(), List.of(), List.of(npc, npc)));
        String json = codec.encode(world(npc));
        for (String invalid : List.of(json.replace("PATROL", "UNKNOWN"),
                json.replace("\"id\":1", "\"id\":-1"),
                json.replace("\"attackAttemptSequence\":0", "\"attackAttemptSequence\":-1"),
                json.replace("\"npcs\":[", "\"npcs\":[null,"),
                json.replace("\"width\":42.0", "\"width\":0"),
                json.replace("\"width\":42.0", "\"width\":null"),
                json.replace(",\"attackAttemptSequence\":0", ""),
                json.replace(",\"facingRight\":true", ""))) {
            assertThrows(ProtocolException.class, () -> codec.decode(invalid), invalid);
        }
    }
}
