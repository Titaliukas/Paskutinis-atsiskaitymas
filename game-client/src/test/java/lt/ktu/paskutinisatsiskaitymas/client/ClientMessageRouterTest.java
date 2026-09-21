package lt.ktu.paskutinisatsiskaitymas.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientMessageRouterTest {
    @Test
    void dispatchesOnlyServerMessages() throws Exception {
        ClientMessageRouter router = new ClientMessageRouter();
        List<Message> received = new ArrayList<>();
        Welcome welcome = new Welcome(UUID.randomUUID(), "Player");
        ErrorMessage error = new ErrorMessage("INVALID_MESSAGE", "Invalid JSON");
        router.route(welcome, received::add, received::add);
        router.route(error, received::add, received::add);
        router.route(new Pong("one"), received::add, received::add);
        assertEquals(List.of(welcome, error), received);
        assertThrows(ProtocolException.class, () -> router.route(new Hello("wrong"), received::add, received::add));
        assertThrows(ProtocolException.class, () -> router.route(new Ping("wrong"), received::add, received::add));
    }

    @Test
    void rejectsInvalidInputAndCannotReconnectAfterClose() {
        try (ClientConnection connection = new ClientConnection(update -> { })) {
            assertThrows(IllegalArgumentException.class, () -> connection.connect(URI.create("http://localhost/game"), "Player"));
            assertThrows(IllegalArgumentException.class, () -> connection.connect(URI.create("ws://localhost/game"), " "));
            connection.close();
            assertThrows(IllegalStateException.class, () -> connection.connect(URI.create("ws://localhost/game"), "Player"));
        }
    }
}
