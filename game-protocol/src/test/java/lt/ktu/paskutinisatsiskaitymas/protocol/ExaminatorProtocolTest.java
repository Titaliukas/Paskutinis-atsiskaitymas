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

    private ExaminatorShotSnapshot shotsFeedback() { return new ExaminatorShotSnapshot(1, 487, 267, 500, 448, 0.2); }

    private ExaminatorSnapshot ability(List<ExaminatorShotSnapshot> shots) {
        return new ExaminatorSnapshot(owner, 480, 270, "FIRE", 27.9, 0.2, 2.1, 1, 487, 267, 500, 448, shots);
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
        List<ExaminatorShotSnapshot> shots = new ArrayList<>(List.of(new ExaminatorShotSnapshot(1, 487, 267, 500, 448, 0.2)));
        WorldSnapshot world = new WorldSnapshot(30, new ArenaSnapshot("training", 960, 540, List.of()),
                List.of(player()), List.of(), List.of(), List.of(), List.of(), List.of(ability(shots)));
        shots.clear();
        String json = codec.encode(world);
        assertEquals(world, codec.decode(json));
        assertEquals(1, world.examinators().getFirst().shots().size());
        for (String field : List.of("prone", "examinators", "shotSequence", "shots", "simulationTime", "emitterX", "aimY")) {
            var tree = new ObjectMapper().readTree(json);
            if (field.equals("prone")) { ((ObjectNode) tree.get("players").get(0)).remove(field); }
            else if (field.equals("examinators")) { ((ObjectNode) tree).remove(field); }
            else { ((ObjectNode) tree.get("examinators").get(0)).remove(field); }
            assertThrows(ProtocolException.class, () -> codec.decode(tree.toString()), field);
        }
        for (String field : List.of("sequence", "originX", "originY", "endpointX", "endpointY", "remainingSeconds")) {
            var tree = new ObjectMapper().readTree(json);
            ((ObjectNode) tree.get("examinators").get(0).get("shots").get(0)).remove(field);
            assertThrows(ProtocolException.class, () -> codec.decode(tree.toString()), field);
        }
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"remainingSeconds\":0.2", "\"remainingSeconds\":0")));
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"shots\":[{", "\"shots\":[null,{")));
        String wire = codec.encode(input);
        assertThrows(ProtocolException.class, () -> codec.decode(wire.replace(",\"examinator\":true", "")));
        assertThrows(ProtocolException.class, () -> codec.decode(wire.replace("\"prone\":true", "\"prone\":null")));
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"FIRE\"", "\"UNKNOWN\"")));
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"shotSequence\":1", "\"shotSequence\":-1")));
        assertThrows(IllegalArgumentException.class, () -> new ExaminatorShotSnapshot(1, Double.NaN, 0, 1, 1, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new ExaminatorShotSnapshot(0, 0, 0, 1, 1, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new ExaminatorShotSnapshot(1, 0, 0, 1, 1, 0.21));
        assertThrows(IllegalArgumentException.class, () -> new ExaminatorSnapshot(owner, 0, 0, "IDLE", 0,
                0, 2.1, 1, 0, 0, 1, 1, List.of(new ExaminatorShotSnapshot(1, 0, 0, 1, 1, 0.2))));
        assertThrows(IllegalArgumentException.class, () -> new ExaminatorSnapshot(owner, 0, 0, "INSPECT", 31,
                0.6, 0, 0, 0, 0, 1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ExaminatorSnapshot(owner, 0, 0, "AIM", 30,
                0.51, 0, 0, 0, 0, 1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WorldSnapshot(0, world.arena(), List.of(player()),
                List.of(), List.of(), List.of(), List.of(), List.of(ability(List.of(shotsFeedback())), ability(List.of(shotsFeedback())))));
        for (String phase : List.of("IDLE", "FLY", "INSPECT", "ACQUIRE", "AIM")) {
            ExaminatorSnapshot state = new ExaminatorSnapshot(owner, 480, 270, phase, 30,
                    phase.equals("IDLE") ? 0 : 0.1, 1, 0, 487, 267, 100, 100, List.of());
            WorldSnapshot snapshot = new WorldSnapshot(1, world.arena(), List.of(player()), List.of(), List.of(),
                    List.of(), List.of(), List.of(state));
            assertEquals(snapshot, codec.decode(codec.encode(snapshot)));
        }
    }
}
