package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Host-only request; the server derives lobby and identity from the connection. */
public record StartGame() implements Message { }
