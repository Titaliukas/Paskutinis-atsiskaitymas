package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lt.ktu.paskutinisatsiskaitymas.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class ServerLobbyMediatorTest {
    private static final class Participant {
        final List<Message> messages = new CopyOnWriteArrayList<>();
        final ConnectionSession session = new ConnectionSession(messages::add);
        <T> T last(Class<T> type) {
            return messages.reversed().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
        }
    }

    @Test
    void mediatorControlsMembershipPermissionsCapacityAndClosure() {
        try (var mediator = new ServerLobbyMediator(ArenaSelection.create("training", null))) {
            Participant host = new Participant();
            mediator.create(host.session, new CreateLobby("Host"));
            String code = host.last(LobbyState.class).code();
            mediator.create(host.session, new CreateLobby("Again"));
            assertEquals("ALREADY_CONNECTED", host.last(ErrorMessage.class).code());
            Participant guest = new Participant();
            mediator.join(guest.session, new JoinLobby(code, "Guest"));
            mediator.start(guest.session);
            assertEquals("HOST_REQUIRED", guest.last(ErrorMessage.class).code());
            mediator.input(guest.session, new InputState(0, true, false, false, false, false));
            assertEquals("GAME_NOT_STARTED", guest.last(ErrorMessage.class).code());
            assertEquals(2, host.last(LobbyState.class).members().size());
            for (int index = 0; index < 2; index++) {
                mediator.join(new Participant().session, new JoinLobby(code, "Other " + index));
            }
            Participant extra = new Participant();
            mediator.join(extra.session, new JoinLobby(code, "Extra"));
            assertEquals("LOBBY_FULL", extra.last(ErrorMessage.class).code());
            assertNull(extra.session.playerId);
            var oldId = guest.session.playerId;
            mediator.disconnected(guest.session);
            mediator.join(extra.session, new JoinLobby(code, "Extra"));
            assertEquals(1, extra.last(Welcome.class).slot());
            assertNotEquals(oldId, extra.session.playerId);
            mediator.disconnected(host.session);
            assertNotNull(extra.last(LobbyClosed.class));
            assertNull(extra.session.lobbyCode);
            Participant late = new Participant();
            mediator.join(late.session, new JoinLobby(code, "Late"));
            assertEquals("LOBBY_NOT_FOUND", late.last(ErrorMessage.class).code());
            mediator.create(extra.session, new CreateLobby("New host"));
            assertEquals(extra.session.playerId, extra.last(LobbyState.class).hostId());
        }
    }

    @Test
    void simultaneousJoinsNeverOverfillAndStartJoinRaceIsAtomic() throws Exception {
        try (var mediator = new ServerLobbyMediator(ArenaSelection.create("training", null));
                var workers = Executors.newFixedThreadPool(8)) {
            Participant host = new Participant();
            mediator.create(host.session, new CreateLobby("Host"));
            String code = host.session.lobbyCode;
            List<Participant> guests = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> new Participant()).toList();
            CountDownLatch gate = new CountDownLatch(1);
            var tasks = guests.stream().map(guest -> workers.submit(() -> {
                gate.await();
                mediator.join(guest.session, new JoinLobby(code, "Guest"));
                return null;
            })).toList();
            gate.countDown();
            for (var task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
            assertEquals(3, guests.stream().filter(guest -> guest.session.playerId != null).count());
            assertEquals(4, host.last(LobbyState.class).members().size());
            assertEquals(4, host.last(LobbyState.class).members().stream().map(LobbyMember::slot).distinct().count());
            mediator.disconnected(host.session);

            for (int iteration = 0; iteration < 10; iteration++) {
                Participant racingHost = new Participant();
                Participant racingGuest = new Participant();
                mediator.create(racingHost.session, new CreateLobby("Host"));
                String racingCode = racingHost.session.lobbyCode;
                CountDownLatch race = new CountDownLatch(1);
                var start = workers.submit(() -> { race.await(); mediator.start(racingHost.session); return null; });
                var join = workers.submit(() -> {
                    race.await(); mediator.join(racingGuest.session, new JoinLobby(racingCode, "Guest")); return null;
                });
                race.countDown();
                start.get(5, TimeUnit.SECONDS);
                join.get(5, TimeUnit.SECONDS);
                LobbyState running = racingHost.last(LobbyState.class);
                assertEquals(LobbyState.Phase.RUNNING, running.phase());
                if (racingGuest.session.playerId == null) {
                    assertEquals(1, running.members().size());
                    assertEquals("LOBBY_STARTED", racingGuest.last(ErrorMessage.class).code());
                } else {
                    assertEquals(2, running.members().size());
                    assertEquals(running, racingGuest.last(LobbyState.class));
                }
                mediator.disconnected(racingHost.session);
            }
        }
    }
}
