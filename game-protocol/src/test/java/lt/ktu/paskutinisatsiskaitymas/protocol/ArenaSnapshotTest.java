package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaSnapshotTest {
    @Test
    void requiresAStableValidatedTransportIdentityInBothDirections() throws Exception {
        JsonMessageCodec codec = new JsonMessageCodec();
        for (String type : List.of("combat", "training")) {
            WorldSnapshot snapshot = new WorldSnapshot(0, new ArenaSnapshot(type, 960, 540, List.of()),
                    List.of(), List.of(), List.of(), List.of(), List.of());
            String json = codec.encode(snapshot);
            assertTrue(json.contains("\"arenaType\":\"" + type + "\""));
            assertFalse(json.contains(".png"));
            assertEquals(snapshot, codec.decode(json));
            for (String invalid : new String[]{"null", "42", "true", "[]", "{}", "\"\"", "\"COMBAT\"", "\"unknown\"",
                    "\"assets/combat_arena_v1.png\""}) {
                String bad = json.replace("\"arenaType\":\"" + type + "\"", "\"arenaType\":" + invalid);
                assertThrows(ProtocolException.class, () -> codec.decode(bad), invalid);
            }
            String missing = json.replace("\"arenaType\":\"" + type + "\",", "");
            assertThrows(ProtocolException.class, () -> codec.decode(missing));
        }
        assertThrows(IllegalArgumentException.class, () -> new ArenaSnapshot(null, 960, 540, List.of()));
    }
}
