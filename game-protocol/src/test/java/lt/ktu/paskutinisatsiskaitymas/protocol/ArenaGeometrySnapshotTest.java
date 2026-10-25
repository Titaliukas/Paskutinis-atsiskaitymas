package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaGeometrySnapshotTest {
    @Test
    void roundTripsPassageAndServicePlatformAndRejectsMissingOrUnknownGeometryFields() throws Exception {
        var codec = new JsonMessageCodec();
        var world = new WorldSnapshot(1, new ArenaSnapshot("combat", 960, 540,
                List.of(new PlatformSnapshot(450, 280, 120, 6, "service")), new SidePassageSnapshot(343, 421)),
                List.of(), List.of(), List.of(), List.of(), List.of());
        String json = codec.encode(world);
        assertEquals(world, codec.decode(json));
        for (String invalid : new String[]{"null", "\"unknown\"", "42", "\"STANDARD\""}) {
            assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"kind\":\"service\"", "\"kind\":" + invalid)));
        }
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace(",\"kind\":\"service\"", "")));
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"openingTop\":343.0,", "")));
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"supportY\":421.0", "\"supportY\":342.0")));
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"supportY\":421.0", "\"supportY\":541.0")));
        assertThrows(ProtocolException.class, () -> codec.decode(json.replace("\"combat\"", "\"training\"")));
    }

    @Test
    void validatesFiniteOpeningBoundsAndKeepsExplicitlyAbsentTrainingPassage() throws Exception {
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new SidePassageSnapshot(invalid, 421));
            assertThrows(IllegalArgumentException.class, () -> new SidePassageSnapshot(343, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new SidePassageSnapshot(421, 421));
        assertThrows(IllegalArgumentException.class, () -> new PlatformSnapshot(0, 0, 120, 6, null));
        var world = new WorldSnapshot(0, new ArenaSnapshot("training", 960, 540, List.of(), null),
                List.of(), List.of(), List.of(), List.of(), List.of());
        var codec = new JsonMessageCodec();
        assertEquals(world, codec.decode(codec.encode(world)));
        assertTrue(codec.encode(world).contains("\"sidePassage\":null"));
        assertThrows(ProtocolException.class, () -> codec.decode(codec.encode(world).replace(",\"sidePassage\":null", "")));
    }
}
