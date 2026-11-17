package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.ArrayList;
import java.util.List;
import lt.ktu.paskutinisatsiskaitymas.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterBuildRoutingTest {
    @Test
    void invalidBuildsNeverAdmitParticipantsOrConsumeLobbySlots() {
        try (var mediator = new ServerLobbyMediator(ArenaSelection.create("combat", null))) {
            MessageRouter router = new MessageRouter(mediator);
            List<Message> messages = new ArrayList<>();
            ConnectionSession host = new ConnectionSession(messages::add);
            router.route(host, new CreateLobby("Host"));
            String code = ((LobbyState) messages.getLast()).code();
            for (StatAllocation invalid : new StatAllocation[]{new StatAllocation(150, 150, 150),
                    new StatAllocation(45, 100, 100), new StatAllocation(100, 51, 100),
                    new StatAllocation(100, 100, 155)}) {
                List<Message> rejected = new ArrayList<>();
                ConnectionSession session = new ConnectionSession(rejected::add);
                router.route(session, new JoinLobby(code, "Bad", "GOGIS", invalid));
                assertEquals("INVALID_STATS", assertInstanceOf(ErrorMessage.class, rejected.getLast()).code());
                assertNull(session.playerId);
                assertNull(session.lobbyCode);
                rejected.clear();
                router.route(session, new CreateLobby("Bad", "GOGIS", invalid));
                assertEquals("INVALID_STATS", assertInstanceOf(ErrorMessage.class, rejected.getLast()).code());
                assertNull(session.lobbyCode);
            }
            for (int slot = 1; slot < 4; slot++) {
                List<Message> accepted = new ArrayList<>();
                router.route(new ConnectionSession(accepted::add), new JoinLobby(code, "Good"));
                assertEquals(slot, assertInstanceOf(Welcome.class, accepted.getFirst()).slot());
            }
        }
    }
}
