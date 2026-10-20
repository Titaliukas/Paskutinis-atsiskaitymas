package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileSnapshotTest {
    private final JsonMessageCodec codec = new JsonMessageCodec();

    private ProjectileSnapshot projectile(String type, boolean returning) {
        return new ProjectileSnapshot(UUID.randomUUID(), 1, type, 456.12345678912345, 232.12345678912345,
                8, -359.12345678912345, 22.12345678912345, returning);
    }

    private WorldSnapshot world(List<ProjectileSnapshot> projectiles) {
        return new WorldSnapshot(123, new ArenaSnapshot(960, 540, List.of()),
                List.of(new PlayerSnapshot(UUID.randomUUID(), 0, "Player", 180, 416, 42, 64,
                        true, false, 247, 100, 100, 0, "PATASIJUS", true)), List.of(),
                List.of(new NPCSnapshot(1, 350, 416, 42, 64, "CHASE", true, 0, true)), List.of(), projectiles);
    }

    @Test
    void roundTripsBothVariantsAndDirectionsWithAuthoritativeEffectFlags() throws Exception {
        for (String type : List.of("BOOK", "DIPLOMA")) {
            for (boolean returning : new boolean[]{false, true}) {
                WorldSnapshot world = world(List.of(projectile(type, returning)));
                String json = codec.encode(world);
                assertEquals(world, codec.decode(json));
                assertTrue(json.contains("\"projectileType\":\"" + type + "\""));
                assertFalse(json.contains("lt.ktu"));
            }
        }
    }

    @Test
    void rejectsDuplicateIdentitiesNullsAndInvalidGeometryAndNames() throws Exception {
        ProjectileSnapshot value = projectile("BOOK", false);
        assertThrows(IllegalArgumentException.class, () -> world(List.of(value, value)));
        assertThrows(NullPointerException.class, () -> world(null));
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new ProjectileSnapshot(UUID.randomUUID(), 1,
                    "BOOK", bad, 0, 8, 360, 0, false));
            assertThrows(IllegalArgumentException.class, () -> new ProjectileSnapshot(UUID.randomUUID(), 1,
                    "BOOK", 0, 0, 8, 360, bad, false));
        }
        String json = codec.encode(world(List.of(value)));
        for (String invalid : List.of(
                json.replace("\"sourceNPCId\":1", "\"sourceNPCId\":0"),
                json.replace("\"projectileType\":\"BOOK\"", "\"projectileType\":\"ROCKET\""),
                json.replace("\"radius\":8.0", "\"radius\":0"),
                json.replace("\"returning\":false", "\"returning\":null"),
                json.replace(",\"returning\":false", ""),
                json.replace(",\"slowed\":true", ""),
                json.replace(",\"stunned\":true", ""),
                json.replace("\"id\":\"" + value.id() + "\"", "\"id\":\"invalid\""))) {
            assertNotEquals(json, invalid);
            assertThrows(ProtocolException.class, () -> codec.decode(invalid));
        }
        String empty = codec.encode(world(List.of()));
        assertThrows(ProtocolException.class, () -> codec.decode(empty.replace(",\"projectiles\":[]", "")));
    }

    @Test
    void fullProductionSnapshotFitsExistingMessageLimit() throws Exception {
        var projectiles = IntStream.range(0, 8).mapToObj(index -> projectile(index % 2 == 0 ? "BOOK" : "DIPLOMA",
                index % 2 == 0)).toList();
        var players = IntStream.range(0, 4).mapToObj(slot -> new PlayerSnapshot(UUID.randomUUID(), slot,
                "Ž".repeat(32), 123.12345678912345, 416.12345678912345, 42, 64, true, true,
                -395.12345678912345, 100, 100, 123456.12345678912345, "PATASIJUS", true)).toList();
        var items = IntStream.range(0, 3).mapToObj(index -> new ItemSnapshot(UUID.randomUUID(),
                "SPEED_BOOST", 123.12345678912345, 456.12345678912345)).toList();
        WorldSnapshot world = new WorldSnapshot(Long.MAX_VALUE, new ArenaSnapshot(960, 540,
                List.of(new PlatformSnapshot(400, 280, 160, 20), new PlatformSnapshot(140, 380, 160, 20),
                        new PlatformSnapshot(660, 380, 160, 20), new PlatformSnapshot(0, 480, 960, 60))),
                players, items, List.of(new NPCSnapshot(1, 350.12345678912345, 416, 42, 64,
                        "CHASE", true, Long.MAX_VALUE, true), new NPCSnapshot(2, 580.12345678912345, 416,
                        42, 64, "CHASE", false, Long.MAX_VALUE, true)), List.of(), projectiles);
        String json = codec.encode(world);
        assertTrue(json.length() <= JsonMessageCodec.MAX_MESSAGE_CHARACTERS, "Full snapshot length: " + json.length());
        assertEquals(world, codec.decode(json));
    }
}
