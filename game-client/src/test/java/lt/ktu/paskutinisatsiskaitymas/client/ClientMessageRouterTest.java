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
        Welcome welcome = new Welcome(UUID.randomUUID(), UUID.randomUUID(), 0, "Player");
        ErrorMessage error = new ErrorMessage("INVALID_MESSAGE", "Invalid JSON");
        WorldSnapshot snapshot = new WorldSnapshot(1,
                new ArenaSnapshot(960, 540, List.of(new PlatformSnapshot(0, 480, 960, 60))), List.of());
        router.route(welcome, received::add, received::add, received::add);
        router.route(snapshot, received::add, received::add, received::add);
        router.route(error, received::add, received::add, received::add);
        router.route(new Pong("one"), received::add, received::add, received::add);
        assertEquals(List.of(welcome, snapshot, error), received);
        assertThrows(ProtocolException.class,
                () -> router.route(new Hello("wrong"), received::add, received::add, received::add));
        assertThrows(ProtocolException.class,
                () -> router.route(new InputState(0, false, false, false),
                        received::add, received::add, received::add));
        assertThrows(ProtocolException.class,
                () -> router.route(new Ping("wrong"), received::add, received::add, received::add));
    }

    @Test
    void rejectsInvalidInputAndCannotReconnectAfterClose() {
        try (ClientConnection connection = new ClientConnection(new ClientEvents() {
            @Override public void onConnectionStatus(ConnectionStatus status) { }
            @Override public void onJoined(Welcome welcome) { }
            @Override public void onSnapshot(WorldSnapshot snapshot) { }
        })) {
            assertThrows(IllegalArgumentException.class, () -> connection.connect(URI.create("http://localhost/game"), "Player"));
            assertThrows(IllegalArgumentException.class, () -> connection.connect(URI.create("ws://localhost/game"), " "));
            connection.close();
            assertThrows(IllegalStateException.class, () -> connection.connect(URI.create("ws://localhost/game"), "Player"));
        }
    }
}
