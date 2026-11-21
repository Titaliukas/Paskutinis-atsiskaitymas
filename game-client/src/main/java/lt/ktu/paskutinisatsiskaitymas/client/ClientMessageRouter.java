package lt.ktu.paskutinisatsiskaitymas.client;

import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.Message;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyState;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyClosed;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyList;
import lt.ktu.paskutinisatsiskaitymas.protocol.Pong;
import lt.ktu.paskutinisatsiskaitymas.protocol.ProtocolException;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** Server-message dispatch boundary, independent of rendering and input. */
final class ClientMessageRouter {
    void route(Message message, ClientEvents listener) throws ProtocolException {
        switch (message) {
            case Welcome value -> listener.onJoined(value);
            case WorldSnapshot value -> listener.onSnapshot(value);
            case GameEventMessage value -> listener.onGameEvent(value);
            case LobbyState value -> listener.onLobbyState(value);
            case LobbyClosed value -> listener.onLobbyClosed(value);
            case LobbyList value -> listener.onLobbyList(value);
            case ErrorMessage value -> listener.onRequestError(value);
            case Pong ignored -> { }
            default -> throw new ProtocolException("Expected a server message", null);
        }
    }
}
