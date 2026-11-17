package lt.ktu.paskutinisatsiskaitymas.client;

import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyState;
import lt.ktu.paskutinisatsiskaitymas.protocol.LobbyClosed;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;

public interface ClientEvents {
    default void onLobbyState(LobbyState state) { }
    default void onLobbyClosed(LobbyClosed closure) { }
    default void onRequestError(ErrorMessage error) { }
    default void onConnectionStatus(ConnectionStatus status) { }
    default void onJoined(Welcome welcome) { }
    default void onSnapshot(WorldSnapshot snapshot) { }
    default void onGameEvent(GameEventMessage event) { }
}
