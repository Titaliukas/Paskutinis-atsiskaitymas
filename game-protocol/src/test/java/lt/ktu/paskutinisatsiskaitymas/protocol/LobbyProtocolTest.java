package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobbyProtocolTest {
    private final JsonMessageCodec codec = new JsonMessageCodec();

    @Test
    void roundTripsEveryLobbyMessageWithExplicitNames() throws Exception {
        UUID host = UUID.randomUUID();
        List<Message> messages = List.of(new CreateLobby("Host", "GOGIS", new StatAllocation(75, 125, 100)),
                new JoinLobby("abc123", "Guest"), new StartGame(),
                new LobbyState("ABC123", host, LobbyState.Phase.WAITING,
                        List.of(new LobbyMember(host, 0, "Host", "GOGIS"))),
                new LobbyState("ABC123", host, LobbyState.Phase.RUNNING,
                        List.of(new LobbyMember(host, 0, "Host", "GOGIS"))),
                new LobbyClosed("The host left."));
        for (Message message : messages) {
            String json = codec.encode(message);
            assertFalse(json.contains("lt.ktu"));
            assertEquals(message, codec.decode(json));
        }
        assertEquals("ABC123", new JoinLobby("  abc123  ", "Guest").code());
        assertEquals(new JoinLobby("ABC123", "Guest"), codec.decode(
                "{\"type\":\"JOIN_LOBBY\",\"code\":\"abc123\",\"nickname\":\"Guest\"}"));
    }

    @Test
    void rejectsOldHandshakeAndMalformedLobbyRequests() {
        for (String json : List.of(
                "{\"type\":\"HELLO\",\"nickname\":\"Old\"}",
                "{\"type\":\"JOIN_LOBBY\",\"nickname\":\"Guest\"}",
                "{\"type\":\"JOIN_LOBBY\",\"code\":123456,\"nickname\":\"Guest\"}",
                "{\"type\":\"JOIN_LOBBY\",\"code\":\"ABC123\",\"nickname\":false}",
                "{\"type\":\"JOIN_LOBBY\",\"code\":\"ABC123\",\"nickname\":\"Guest\",\"extra\":1}",
                "{\"type\":\"START_GAME\",\"hostId\":\"spoofed\"}",
                "{\"type\":\"LOBBY_CLOSED\",\"reason\":\"\"}")) {
            assertThrows(ProtocolException.class, () -> codec.decode(json), json);
        }
        for (String code : List.of("", "ABC", "ABC1234", "AB!123")) {
            assertThrows(IllegalArgumentException.class, () -> new JoinLobby(code, "Guest"));
        }
    }

    @Test
    void rejectsInvalidRostersAndDefensivelyCopiesMembers() {
        UUID host = UUID.randomUUID();
        LobbyMember member = new LobbyMember(host, 0, "Host", "GOGIS");
        assertThrows(IllegalArgumentException.class,
                () -> new LobbyState("ABC123", host, LobbyState.Phase.WAITING, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new LobbyState("ABC123", UUID.randomUUID(), LobbyState.Phase.WAITING, List.of(member)));
        assertThrows(IllegalArgumentException.class,
                () -> new LobbyState("ABC123", host, LobbyState.Phase.WAITING, List.of(member, member)));
        assertThrows(IllegalArgumentException.class, () -> new LobbyMember(host, 4, "Host", "GOGIS"));
        var source = new java.util.ArrayList<>(List.of(member));
        LobbyState state = new LobbyState("ABC123", host, LobbyState.Phase.WAITING, source);
        source.clear();
        assertEquals(1, state.members().size());
    }
}
