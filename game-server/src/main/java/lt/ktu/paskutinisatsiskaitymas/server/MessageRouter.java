package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.Objects;
import lt.ktu.paskutinisatsiskaitymas.protocol.CreateLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.JoinLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.LeaveLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.ListLobbies;
import lt.ktu.paskutinisatsiskaitymas.protocol.SelectCharacter;
import lt.ktu.paskutinisatsiskaitymas.protocol.StartGame;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.InputState;
import lt.ktu.paskutinisatsiskaitymas.protocol.Message;
import lt.ktu.paskutinisatsiskaitymas.protocol.Ping;
import lt.ktu.paskutinisatsiskaitymas.protocol.Pong;

/** Transport dispatch delegates all participant coordination to the mediator. */
final class MessageRouter {
    private final LobbyMediator mediator;

    MessageRouter(LobbyMediator mediator) {
        this.mediator = Objects.requireNonNull(mediator, "mediator");
    }

    void route(ConnectionSession session, Message message) {
        if (message instanceof CreateLobby request) {
            mediator.create(session, request);
        } else if (message instanceof JoinLobby request) {
            mediator.join(session, request);
        } else if (message instanceof ListLobbies request) {
            mediator.list(session, request);
        } else if (message instanceof SelectCharacter request) {
            mediator.select(session, request);
        } else if (message instanceof LeaveLobby) {
            mediator.leave(session);
        } else if (message instanceof StartGame) {
            mediator.start(session);
        } else if (message instanceof InputState input) {
            mediator.input(session, input);
        } else if (message instanceof Ping ping) {
            session.send(new Pong(ping.requestId()));
        } else {
            session.send(new ErrorMessage("UNEXPECTED_MESSAGE", "Expected a client message"));
        }
    }

    void disconnected(ConnectionSession session) {
        mediator.disconnected(session);
    }
}
