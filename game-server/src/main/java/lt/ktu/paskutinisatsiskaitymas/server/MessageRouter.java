package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.Objects;
import lt.ktu.paskutinisatsiskaitymas.application.BoundedCommandQueue;
import lt.ktu.paskutinisatsiskaitymas.application.GameCommand;
import lt.ktu.paskutinisatsiskaitymas.application.InputCommand;
import lt.ktu.paskutinisatsiskaitymas.application.JoinPlayerCommand;
import lt.ktu.paskutinisatsiskaitymas.application.LeavePlayerCommand;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import lt.ktu.paskutinisatsiskaitymas.protocol.InputState;
import lt.ktu.paskutinisatsiskaitymas.protocol.Message;
import lt.ktu.paskutinisatsiskaitymas.protocol.Ping;
import lt.ktu.paskutinisatsiskaitymas.protocol.Pong;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;

/**
 * Validates message direction and translates client DTOs into application commands.
 * It reserves transport slots but never mutates authoritative world state.
 */
final class MessageRouter {
    private final BoundedCommandQueue<GameCommand> commands;
    private final PlayerSlots slots;

    MessageRouter(BoundedCommandQueue<GameCommand> commands, PlayerSlots slots) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.slots = Objects.requireNonNull(slots, "slots");
    }

    Message route(ConnectionSession session, Message message) {
        synchronized (session) {
            if (message instanceof Hello hello) {
                if (session.playerId != null) {
                    return new ErrorMessage("ALREADY_CONNECTED", "HELLO was already accepted");
                }
                synchronized (slots) {
                    PlayerSlots.Assignment assignment = slots.reserve(session.id).orElse(null);
                    if (assignment == null) {
                        return new ErrorMessage("SERVER_FULL", "The two player slots are occupied");
                    }
                    boolean queued = commands.offerAfterEvicting(new JoinPlayerCommand(
                            assignment.playerId(), assignment.slot(), hello.nickname()),
                            queuedCommand -> queuedCommand instanceof InputCommand);
                    if (!queued) {
                        slots.release(session.id);
                        return new ErrorMessage("SERVER_BUSY", "The command queue is full");
                    }
                    session.playerId = assignment.playerId();
                    session.slot = assignment.slot();
                    session.nickname = hello.nickname();
                }
                return new Welcome(session.id, session.playerId, session.slot, session.nickname);
            }
            if (session.playerId == null) {
                return new ErrorMessage("HANDSHAKE_REQUIRED", "Send HELLO first");
            }
            if (message instanceof InputState input) {
                boolean accepted = commands.offer(new InputCommand(session.playerId, input.sequence(),
                        input.left(), input.right(), input.jump()));
                return accepted ? null : new ErrorMessage("SERVER_BUSY", "The command queue is full");
            }
            if (message instanceof Ping ping) {
                return new Pong(ping.requestId());
            }
            return new ErrorMessage("UNEXPECTED_MESSAGE", "Expected a client message");
        }
    }

    void disconnected(ConnectionSession session) {
        synchronized (session) {
            if (session.playerId != null) {
                synchronized (slots) {
                    boolean queued = commands.offerAfterEvicting(new LeavePlayerCommand(session.playerId),
                            queuedCommand -> queuedCommand instanceof InputCommand);
                    if (!queued) {
                        System.getLogger(MessageRouter.class.getName()).log(System.Logger.Level.ERROR,
                                "Command queue contains only lifecycle events; retaining slot for {0}",
                                session.playerId);
                        return;
                    }
                    slots.release(session.id);
                    session.playerId = null;
                    session.slot = -1;
                    session.nickname = null;
                }
            }
        }
    }
}
