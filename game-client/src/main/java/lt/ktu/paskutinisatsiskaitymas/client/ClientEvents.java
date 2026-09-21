package lt.ktu.paskutinisatsiskaitymas.client;

import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** Presentation-facing events emitted by the asynchronous network adapter. */
public interface ClientEvents {
    void onConnectionStatus(ConnectionStatus status);
    void onJoined(Welcome welcome);
    void onSnapshot(WorldSnapshot snapshot);
}
