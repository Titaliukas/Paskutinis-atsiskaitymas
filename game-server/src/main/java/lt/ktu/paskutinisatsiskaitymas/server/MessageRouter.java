package lt.ktu.paskutinisatsiskaitymas.server;

import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import lt.ktu.paskutinisatsiskaitymas.protocol.Message;
import lt.ktu.paskutinisatsiskaitymas.protocol.Ping;
import lt.ktu.paskutinisatsiskaitymas.protocol.Pong;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;

/**
 * Handles connection messages only. Future gameplay DTOs must be translated into application
 * commands and offered to CommandQueue here, never executed on WebSocket callback threads.
 */
final class MessageRouter {
    Message route(ConnectionSession session, Message message) {
        synchronized (session) {
            if (message instanceof Hello hello) {
                if (session.nickname != null) {
                    return new ErrorMessage("ALREADY_CONNECTED", "HELLO was already accepted");
                }
                session.nickname = hello.nickname();
                return new Welcome(session.id, session.nickname);
            }
            if (session.nickname == null) {
                return new ErrorMessage("HANDSHAKE_REQUIRED", "Send HELLO first");
            }
            if (message instanceof Ping ping) {
                return new Pong(ping.requestId());
            }
            return new ErrorMessage("UNEXPECTED_MESSAGE", "Expected a client message");
        }
    }
}
