package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Leaves the current room while keeping the server connection open. */
public record LeaveLobby() implements Message { }
