package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JsonMessageCodecTest {
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private static final UUID CONNECTION_ID = UUID.fromString("4d696088-fdd8-4264-9cbe-ffce4ab038cb");
    private static final UUID PLAYER_ID = UUID.fromString("66a72e7e-176f-45d7-ae57-3fc543580d66");

    static Stream<Message> messages() {
        return Stream.of(new Hello("Žaidėjas \"A\""),
                new Welcome(CONNECTION_ID, PLAYER_ID, 0, "Player"),
                new Ping("request-1"), new Pong("request-1"),
                new InputState(7, true, false, true),
                new WorldSnapshot(42,
                        new ArenaSnapshot(960, 540, List.of(new PlatformSnapshot(0, 480, 960, 60))),
                        List.of(new PlayerSnapshot(PLAYER_ID, 0, "Player", 180, 416, 42, 64, true))),
                new ErrorMessage("INVALID_MESSAGE", "Invalid message"));
    }

    @ParameterizedTest
    @MethodSource("messages")
    void roundTripsEveryMessage(Message message) throws Exception {
        assertEquals(message, codec.decode(codec.encode(message)));
    }

    @Test
    void usesStableWireNamesWithoutJavaClassNames() throws Exception {
        assertEquals("{\"type\":\"HELLO\",\"nickname\":\"Player\"}", codec.encode(new Hello("Player")));
        assertEquals(new Ping("a"), codec.decode("{\"type\":\"PING\",\"requestId\":\"a\"}"));
        assertTrue(codec.encode(new InputState(0, false, true, false)).startsWith("{\"type\":\"INPUT\""));
        assertFalse(codec.encode(messages().toList().get(5)).contains("lt.ktu"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "null", "[]", "{}", "not json",
        "{\"type\":\"MOVE\"}",
        "{\"type\":\"java.lang.Runtime\"}",
        "{\"type\":\"HELLO\"}",
        "{\"type\":\"HELLO\",\"nickname\":\" \"}",
        "{\"type\":\"HELLO\",\"nickname\":null}",
        "{\"type\":\"HELLO\",\"nickname\":\"A\",\"extra\":true}",
        "{\"type\":\"HELLO\",\"nickname\":\"A\",\"nickname\":\"B\"}",
        "{\"type\":\"HELLO\",\"nickname\":\"A\"} {}",
        "{\"type\":\"INPUT\",\"sequence\":-1,\"left\":false,\"right\":false,\"jump\":false}",
        "{\"type\":\"INPUT\",\"sequence\":0,\"left\":false,\"right\":false,\"jump\":false,\"x\":100}",
        "{\"type\":\"WELCOME\",\"connectionId\":\"bad-id\",\"playerId\":\"66a72e7e-176f-45d7-ae57-3fc543580d66\",\"slot\":0,\"nickname\":\"A\"}"})
    void rejectsMalformedOrUnsupportedMessages(String json) {
        assertThrows(ProtocolException.class, () -> codec.decode(json));
    }

    @Test
    void rejectsOversizedMessagesAndFields() {
        assertThrows(ProtocolException.class, () -> codec.decode(" ".repeat(JsonMessageCodec.MAX_MESSAGE_CHARACTERS + 1)));
        assertThrows(IllegalArgumentException.class, () -> new Hello("x".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> new Welcome(CONNECTION_ID, PLAYER_ID, 2, "Player"));
        assertThrows(IllegalArgumentException.class, () -> new PlayerSnapshot(
                PLAYER_ID, 0, "Player", 0, 0, -1, 64, false));
        assertThrows(ProtocolException.class, () -> codec.encode(null));
    }
}
