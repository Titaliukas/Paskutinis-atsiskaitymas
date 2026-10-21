package lt.ktu.paskutinisatsiskaitymas.server;

import lt.ktu.paskutinisatsiskaitymas.application.BoundedCommandQueue;
import lt.ktu.paskutinisatsiskaitymas.application.GameCommand;
import lt.ktu.paskutinisatsiskaitymas.application.JoinPlayerCommand;
import lt.ktu.paskutinisatsiskaitymas.application.MatchController;
import lt.ktu.paskutinisatsiskaitymas.domain.CharacterBuild;
import lt.ktu.paskutinisatsiskaitymas.domain.GameSession;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import lt.ktu.paskutinisatsiskaitymas.protocol.StatAllocation;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterBuildRoutingTest {
    @Test
    void invalidBuildsNeitherReserveSlotsNorQueueCommands() {
        BoundedCommandQueue<GameCommand> commands = new BoundedCommandQueue<>(8);
        PlayerSlots slots = new PlayerSlots();
        MessageRouter router = new MessageRouter(commands, slots);
        for (StatAllocation invalid : new StatAllocation[]{new StatAllocation(150, 150, 150),
                new StatAllocation(45, 100, 100), new StatAllocation(100, 51, 100),
                new StatAllocation(100, 100, 155)}) {
            ConnectionSession session = new ConnectionSession();
            var error = assertInstanceOf(ErrorMessage.class, router.route(session, new Hello("Bad", "GOGIS", invalid)));
            assertEquals("INVALID_STATS", error.code());
            assertNull(session.playerId);
            assertTrue(slots.release(session.id).isEmpty());
            assertEquals(0, commands.size());
        }
        for (int slot = 0; slot < 4; slot++) {
            var welcome = assertInstanceOf(Welcome.class,
                    router.route(new ConnectionSession(), new Hello("Good")));
            assertEquals(slot, welcome.slot());
        }
    }

    @Test
    void joinCarriesBuildIntoWorldAndSnapshotReportsCustomMaximumHealth() {
        BoundedCommandQueue<GameCommand> commands = new BoundedCommandQueue<>(8);
        MessageRouter router = new MessageRouter(commands, new PlayerSlots());
        assertInstanceOf(Welcome.class, router.route(new ConnectionSession(),
                new Hello("Custom", "VACIJUS", new StatAllocation(125, 75, 100))));
        JoinPlayerCommand command = assertInstanceOf(JoinPlayerCommand.class, commands.poll().orElseThrow());
        assertEquals(new CharacterBuild(125, 75, 100), command.build());
        GameSession session = GameSession.createDefault();
        new MatchController(session).handle(command);
        var player = SnapshotMapper.map(1, session).players().getFirst();
        assertEquals("VACIJUS", player.character());
        assertEquals(125, player.maxHealth());
        assertEquals(125, player.health());
    }
}
