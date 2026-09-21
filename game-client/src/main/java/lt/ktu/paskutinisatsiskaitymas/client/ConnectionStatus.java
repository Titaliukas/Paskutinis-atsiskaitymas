package lt.ktu.paskutinisatsiskaitymas.client;

/** Presentation-neutral connection feedback; the Swing adapter dispatches it onto the EDT. */
public record ConnectionStatus(State state, String detail) {
    public enum State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
}
