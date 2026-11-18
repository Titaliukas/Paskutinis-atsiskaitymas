package lt.ktu.paskutinisatsiskaitymas.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExaminatorProtocolTest {
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final UUID owner = UUID.randomUUID();

    private PlayerSnapshot player() {
        return new PlayerSnapshot(owner, 0, "Owner", 100, 416, 42, 64, true, false, 0, 100, 100, 0, "GOGIS", false, true);
    }

    private ExaminatorSnapshot ability(List<ShotEndpoint> endpoints) {
        return new ExaminatorSnapshot(owner, 121, 388, "FIRE", 9.5, 0.25, 1, 121, 388, endpoints);
    }

    @Test
    void roundTripsDetachedProneInputAndEndpointFeedbackAndRequiresNewFields() throws Exception {
        InputState input = new InputState(12, true, false, false, false, true, true, true);
        String inputJson = codec.encode(input);
        assertEquals(input, codec.decode(inputJson));
        for (String field : List.of("examinator", "prone")) {
            for (String invalid : List.of("null", "1", "\"true\"", "[]")) {
                assertThrows(ProtocolException.class, () -> codec.decode(inputJson.replace(
                        "\"" + field + "\":true", "\"" + field + "\":" + invalid)));
            }
            assertThrows(ProtocolException.class, () -> codec.decode(inputJson.replace(
                    ",\"" + field + "\":true", "")));
        }
        List<ShotEndpoint> endpoints = new ArrayList<>(List.of(new ShotEndpoint(500, 448)));
        WorldSnapshot world = new WorldSnapshot(30, new ArenaSnapshot("training", 960, 540, List.of()),
                List.of(player()), List.of(), List.of(), List.of(), List.of(), List.of(ability(endpoints)));
        endpoints.clear();
        String json = codec.encode(world);
        assertEquals(world, codec.decode(json));
        assertEquals(1, world.examinators().getFirst().endpoints().size());
        for (String field : List.of("prone", "examinators", "volleySequence", "endpoints", "originX")) {
            var tree = new ObjectMapper().readTree(json);
            if (field.equals("prone")) { ((ObjectNode) tree.get("players").get(0)).remove(field); }
            else if (field.equals("examinators")) { ((ObjectNode) tree).remove(field); }
            else { ((ObjectNode) tree.get("examinators").get(0)).remove(field); }
            assertThrows(ProtocolException.class, () -> codec.decode(tree.toString()), field);
        }
        String wire = codec.encode(input);
        assertThrows(ProtocolException.class, () -> codec.decode(wire.replace(",\"examinator\":true", "")));
        assertThrows(ProtocolException.class, () -> codec.decode(wire.replace("\"prone\":true", "\"prone\":null")));
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"FIRE\"", "\"UNKNOWN\"")));
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"volleySequence\":1", "\"volleySequence\":-1")));
        assertThrows(IllegalArgumentException.class, () -> new ShotEndpoint(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExaminatorSnapshot(owner, 0, 0, "IDLE", 0,
                0, 1, 0, 0, List.of(new ShotEndpoint(1, 1))));
        assertThrows(IllegalArgumentException.class, () -> new ExaminatorSnapshot(owner, 0, 0, "SCAN", 11,
                0.5, 0, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WorldSnapshot(0, world.arena(), List.of(player()),
                List.of(), List.of(), List.of(), List.of(), List.of(ability(List.of()), ability(List.of()))));
    }
}
