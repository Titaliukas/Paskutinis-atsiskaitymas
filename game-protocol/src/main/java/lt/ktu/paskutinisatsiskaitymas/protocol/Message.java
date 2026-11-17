package lt.ktu.paskutinisatsiskaitymas.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Closed wire-message vocabulary. Explicit names prevent deserialization of arbitrary Java types. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CreateLobby.class, name = "CREATE_LOBBY"),
    @JsonSubTypes.Type(value = JoinLobby.class, name = "JOIN_LOBBY"),
    @JsonSubTypes.Type(value = StartGame.class, name = "START_GAME"),
    @JsonSubTypes.Type(value = LobbyState.class, name = "LOBBY_STATE"),
    @JsonSubTypes.Type(value = LobbyClosed.class, name = "LOBBY_CLOSED"),
    @JsonSubTypes.Type(value = Welcome.class, name = "WELCOME"),
    @JsonSubTypes.Type(value = Ping.class, name = "PING"),
    @JsonSubTypes.Type(value = Pong.class, name = "PONG"),
    @JsonSubTypes.Type(value = InputState.class, name = "INPUT"),
    @JsonSubTypes.Type(value = WorldSnapshot.class, name = "WORLD_SNAPSHOT"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR"),
    @JsonSubTypes.Type(value = GameEventMessage.class, name = "GAME_EVENT")
})
public sealed interface Message permits CreateLobby, JoinLobby, StartGame, LobbyState, LobbyClosed, Welcome, Ping, Pong, InputState, WorldSnapshot, ErrorMessage, GameEventMessage {
}
