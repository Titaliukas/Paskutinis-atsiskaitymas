package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.GameSession;
import lt.ktu.paskutinisatsiskaitymas.domain.MovementInput;
import lt.ktu.paskutinisatsiskaitymas.domain.Player;

/**
 * Applies validated application commands and owns input state. All methods are game-loop-thread confined.
 */
public final class MatchController {
    private final GameSession session;
    private final Map<UUID, InputTracker> inputByPlayer = new HashMap<>();

    public MatchController(GameSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public GameSession session() {
        return session;
    }

    public void handle(GameCommand command) {
        Objects.requireNonNull(command, "command");
        if (command instanceof JoinPlayerCommand join) {
            session.addPlayer(new Player(join.playerId(), join.nickname()), join.slot());
            inputByPlayer.put(join.playerId(), new InputTracker());
        } else if (command instanceof LeavePlayerCommand leave) {
            session.removePlayer(leave.playerId());
            inputByPlayer.remove(leave.playerId());
        } else if (command instanceof InputCommand input) {
            InputTracker tracker = inputByPlayer.get(input.playerId());
            if (tracker != null) {
                tracker.update(input);
            }
        }
    }

    public void advance(double seconds) {
        Map<UUID, MovementInput> current = new HashMap<>();
        inputByPlayer.forEach((playerId, input) -> current.put(playerId, input.forTick()));
        session.advance(current, seconds);
    }

    private static final class InputTracker {
        private long lastSequence = -1;
        private boolean left;
        private boolean right;
        private boolean jumpHeld;
        private boolean jumpRequested;

        void update(InputCommand input) {
            if (input.sequence() <= lastSequence) {
                return;
            }
            lastSequence = input.sequence();
            left = input.left();
            right = input.right();
            jumpRequested |= input.jump() && !jumpHeld;
            jumpHeld = input.jump();
        }

        MovementInput forTick() {
            MovementInput result = new MovementInput(left, right, jumpRequested);
            jumpRequested = false;
            return result;
        }
    }
}
