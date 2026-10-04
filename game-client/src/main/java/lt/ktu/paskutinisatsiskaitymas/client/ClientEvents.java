package lt.ktu.paskutinisatsiskaitymas.client;

import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;

public interface ClientEvents {
    default void onConnectionStatus(ConnectionStatus status) { }
    default void onJoined(Welcome welcome) { }
    default void onSnapshot(WorldSnapshot snapshot) { }
    default void onGameEvent(GameEventMessage event) { }
}
