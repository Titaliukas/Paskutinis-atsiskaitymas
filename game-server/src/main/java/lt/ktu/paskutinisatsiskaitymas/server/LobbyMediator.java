package lt.ktu.paskutinisatsiskaitymas.server;

import lt.ktu.paskutinisatsiskaitymas.protocol.CreateLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.JoinLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.InputState;
import lt.ktu.paskutinisatsiskaitymas.protocol.ListLobbies;
import lt.ktu.paskutinisatsiskaitymas.protocol.SelectCharacter;

/** Coordinates participants without allowing them to communicate with one another directly. */
interface LobbyMediator extends AutoCloseable {
    void create(ConnectionSession participant, CreateLobby request);
    void join(ConnectionSession participant, JoinLobby request);
    void list(ConnectionSession participant, ListLobbies request);
    void select(ConnectionSession participant, SelectCharacter request);
    void leave(ConnectionSession participant);
    void start(ConnectionSession participant);
    void input(ConnectionSession participant, InputState input);
    void disconnected(ConnectionSession participant);
    @Override void close();
}
