package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.GameConstants;
import lt.ktu.paskutinisatsiskaitymas.domain.GameSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchControllerTest {
    @Test
    void validatesCommandsAndBoundsQueue() {
        UUID playerId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new JoinPlayerCommand(playerId, 2, "Player"));
        assertThrows(IllegalArgumentException.class, () -> new JoinPlayerCommand(playerId, 0, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new InputCommand(playerId, -1, false, false, false));

        BoundedCommandQueue<GameCommand> queue = new BoundedCommandQueue<>(1);
        assertTrue(queue.offer(new LeavePlayerCommand(playerId)));
        assertFalse(queue.offer(new LeavePlayerCommand(UUID.randomUUID())));
        assertEquals(1, queue.size());
        assertTrue(queue.poll().isPresent());
        assertTrue(queue.poll().isEmpty());

        InputCommand input = new InputCommand(playerId, 0, false, true, false);
        LeavePlayerCommand leave = new LeavePlayerCommand(playerId);
        assertTrue(queue.offer(input));
        assertTrue(queue.offerAfterEvicting(leave, command -> command instanceof InputCommand));
        assertEquals(leave, queue.poll().orElseThrow());
    }

    @Test
    void ignoresStaleInputAndConsumesJumpPressOnce() {
        UUID playerId = UUID.randomUUID();
        GameSession session = GameSession.createDefault();
        MatchController controller = new MatchController(session);
        controller.handle(new JoinPlayerCommand(playerId, 0, "Player"));
        double startX = session.characters().getFirst().x();

        controller.handle(new InputCommand(playerId, 2, false, true, false));
        controller.handle(new InputCommand(playerId, 1, true, false, false));
        controller.advance(GameConstants.TICK_SECONDS);
        assertTrue(session.characters().getFirst().x() > startX, "Older input must not replace newer state");

        controller.handle(new InputCommand(playerId, 3, false, false, true));
        controller.advance(GameConstants.TICK_SECONDS);
        double firstVelocity = session.characters().getFirst().velocityY();
        controller.advance(GameConstants.TICK_SECONDS);
        assertEquals(firstVelocity + GameConstants.GRAVITY * GameConstants.TICK_SECONDS,
                session.characters().getFirst().velocityY(), 0.000_001);
    }

    @Test
    void loopDrainsCommandsBeforeAdvancing() {
        UUID playerId = UUID.randomUUID();
        GameSession session = GameSession.createDefault();
        MatchController controller = new MatchController(session);
        BoundedCommandQueue<GameCommand> queue = new BoundedCommandQueue<>(8);
        AuthoritativeGameLoop loop = new AuthoritativeGameLoop(queue, controller);
        queue.offer(new JoinPlayerCommand(playerId, 0, "Player"));
        queue.offer(new InputCommand(playerId, 0, false, true, false));

        loop.tick();

        assertEquals(1, loop.tickNumber());
        assertEquals(1, session.characters().size());
        assertTrue(session.characters().getFirst().x() > GameConstants.FIRST_SPAWN_X);
    }
}
