package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Application-level client liveness request, distinct from WebSocket control frames. */
public record Ping(String requestId) implements Message {
    public Ping {
        requestId = WireText.require(requestId, "requestId", 64);
    }
}
