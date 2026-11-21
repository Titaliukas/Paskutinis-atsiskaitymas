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
                new ArenaSnapshot("combat", 960, 540, List.of(new PlatformSnapshot(0, 480, 960, 60))), List.of(), List.of(),
                List.of(new NPCSnapshot(1, 300, 416, 42, 64, "CHASE", true, 0, false)), List.of(), List.of());
        ClientEvents observer = new ClientEvents() {
            @Override public void onJoined(Welcome value) { received.add(value); }
            @Override public void onSnapshot(WorldSnapshot value) { received.add(value); }
            @Override public void onRequestError(ErrorMessage value) { received.add(value); }
            @Override public void onLobbyList(LobbyList value) { received.add(value); }
        };
        LobbyList list = new LobbyList(List.of(), false);
        for (Message message : List.of(welcome, snapshot, error, list, new Pong("one"))) {
            router.route(message, observer);
        }
        assertEquals(List.of(welcome, snapshot, error, list), received);
        for (Message message : List.of(new CreateLobby("wrong"), new ListLobbies(null), new Ping("wrong"),
                new InputState(0L, false, false, false, false, false))) {
            assertThrows(ProtocolException.class, () -> router.route(message, observer));
        }
    }

    @Test
    void rejectsInvalidInputAndCannotReconnectAfterClose() {
        try (ClientConnection connection = new ClientConnection(new ClientEvents() {
            @Override public void onConnectionStatus(ConnectionStatus status) { }
            @Override public void onJoined(Welcome welcome) { }
            @Override public void onSnapshot(WorldSnapshot snapshot) { }
        })) {
            assertThrows(IllegalArgumentException.class, () -> connection.connect(URI.create("http://localhost/game")));
            connection.close();
            assertThrows(IllegalStateException.class, () -> connection.connect(URI.create("ws://localhost/game")));
        }
    }
}