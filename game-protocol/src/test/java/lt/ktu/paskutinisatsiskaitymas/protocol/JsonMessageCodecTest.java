package lt.ktu.paskutinisatsiskaitymas.protocol;

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

    static Stream<Message> messages() {
        return Stream.of(new Hello("Žaidėjas \"A\""),
                new Welcome(UUID.fromString("4d696088-fdd8-4264-9cbe-ffce4ab038cb"), "Player"),
                new Ping("request-1"), new Pong("request-1"),
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
        "{\"type\":\"WELCOME\",\"connectionId\":\"bad-id\",\"nickname\":\"A\"}"})
    void rejectsMalformedOrUnsupportedMessages(String json) {
        assertThrows(ProtocolException.class, () -> codec.decode(json));
    }

    @Test
    void rejectsOversizedMessagesAndFields() {
        assertThrows(ProtocolException.class, () -> codec.decode(" ".repeat(JsonMessageCodec.MAX_MESSAGE_CHARACTERS + 1)));
        assertThrows(IllegalArgumentException.class, () -> new Hello("x".repeat(33)));
        assertThrows(ProtocolException.class, () -> codec.encode(null));
    }
}
