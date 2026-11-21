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
    void browserPaginatesOpenRoomsAndOmitsFullAndRunningRooms() {
        try (var mediator = new ServerLobbyMediator(ArenaSelection.create("training", null))) {
            List<String> expected = new java.util.ArrayList<>();
            for (int index = 0; index < LobbyList.PAGE_SIZE + 3; index++) {
                var host = new Participant();
                mediator.create(host.session, new CreateLobby("Open " + index));
                expected.add(host.session.lobbyCode);
            }
            expected.sort(String::compareTo);
            var running = new Participant();
            mediator.create(running.session, new CreateLobby("Running host"));
            mediator.select(running.session, new SelectCharacter("GOGIS", StatAllocation.DEFAULT));
            mediator.start(running.session);
            var full = new Participant();
            mediator.create(full.session, new CreateLobby("Full host"));
            for (int index = 0; index < 3; index++) {
                mediator.join(new Participant().session, new JoinLobby(full.session.lobbyCode, "Guest"));
            }
            var browser = new Participant();
            mediator.list(browser.session, new ListLobbies(null));
            LobbyList first = browser.last(LobbyList.class);
            assertTrue(first.hasMore());
            assertEquals(expected.subList(0, LobbyList.PAGE_SIZE),
                    first.rooms().stream().map(LobbySummary::code).toList());
            assertTrue(first.rooms().stream().allMatch(room -> room.playerCount() == 1));
            mediator.list(browser.session, new ListLobbies(first.rooms().getLast().code()));
            LobbyList second = browser.last(LobbyList.class);
            assertFalse(second.hasMore());
            assertEquals(expected.subList(LobbyList.PAGE_SIZE, expected.size()),
                    second.rooms().stream().map(LobbySummary::code).toList());
            mediator.join(browser.session, new JoinLobby(full.session.lobbyCode, "Too late"));
            assertEquals("LOBBY_FULL", browser.last(ErrorMessage.class).code());
            mediator.join(browser.session, new JoinLobby(running.session.lobbyCode, "Too late"));
            assertEquals("LOBBY_STARTED", browser.last(ErrorMessage.class).code());
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
                mediator.select(racingHost.session, new SelectCharacter("GOGIS", StatAllocation.DEFAULT));
                String racingCode = racingHost.session.lobbyCode;
                CountDownLatch race = new CountDownLatch(1);
                var start = workers.submit(() -> { race.await(); mediator.start(racingHost.session); return null; });
                var join = workers.submit(() -> {
                    race.await(); mediator.join(racingGuest.session, new JoinLobby(racingCode, "Guest")); return null;
                });
                race.countDown();
                start.get(5, TimeUnit.SECONDS);
                join.get(5, TimeUnit.SECONDS);
                LobbyState state = racingHost.last(LobbyState.class);
                if (racingGuest.session.playerId == null) {
                    assertEquals(LobbyState.Phase.RUNNING, state.phase());
                    assertEquals(1, state.members().size());
                    assertEquals("LOBBY_STARTED", racingGuest.last(ErrorMessage.class).code());
                } else {
                    assertEquals(LobbyState.Phase.WAITING, state.phase());
                    assertEquals(2, state.members().size());
                    assertEquals("CHARACTERS_PENDING", racingHost.last(ErrorMessage.class).code());
                }
                mediator.disconnected(racingHost.session);
            }
        }
    }
}
