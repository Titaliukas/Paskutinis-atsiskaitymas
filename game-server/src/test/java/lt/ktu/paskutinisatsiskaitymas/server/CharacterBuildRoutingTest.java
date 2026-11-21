package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.ArrayList;
import java.util.List;
import lt.ktu.paskutinisatsiskaitymas.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterBuildRoutingTest {
    @Test
    void admissionReservesSlotBeforeBuildAndConfirmationCanBeRetriedButNotReplaced() {
        try (var mediator = new ServerLobbyMediator(ArenaSelection.create("combat", null))) {
            var router = new MessageRouter(mediator);
            List<Message> hostMessages = new ArrayList<>();
            List<Message> guestMessages = new ArrayList<>();
            var host = new ConnectionSession(hostMessages::add);
            var guest = new ConnectionSession(guestMessages::add);
            router.route(host, new CreateLobby("Host"));
            String code = assertInstanceOf(LobbyState.class, hostMessages.getLast()).code();
            router.route(guest, new JoinLobby(code, "Guest"));
            var initial = assertInstanceOf(LobbyState.class, guestMessages.getLast());
            assertTrue(initial.members().stream().allMatch(member -> member.character() == null));
            assertEquals(1, guest.slot);
            router.route(host, new SelectCharacter("PATASIJUS", StatAllocation.DEFAULT));
            router.route(host, new StartGame());
            assertEquals("CHARACTERS_PENDING", assertInstanceOf(ErrorMessage.class, hostMessages.getLast()).code());
            for (StatAllocation invalid : List.of(new StatAllocation(150, 150, 150),
                    new StatAllocation(45, 100, 100), new StatAllocation(100, 51, 100),
                    new StatAllocation(100, 100, 155))) {
                router.route(guest, new SelectCharacter("GOGIS", invalid));
                assertEquals("INVALID_STATS", assertInstanceOf(ErrorMessage.class, guestMessages.getLast()).code());
                assertEquals(1, guest.slot);
            }
            router.route(guest, new SelectCharacter("GOGIS", new StatAllocation(125, 125, 50)));
            var confirmed = assertInstanceOf(LobbyState.class, guestMessages.getLast());
            assertEquals("GOGIS", confirmed.members().get(1).character());
            router.route(guest, new SelectCharacter("VACIJUS", StatAllocation.DEFAULT));
            assertEquals("CHARACTER_CONFIRMED", assertInstanceOf(ErrorMessage.class, guestMessages.getLast()).code());
        }
    }
}
