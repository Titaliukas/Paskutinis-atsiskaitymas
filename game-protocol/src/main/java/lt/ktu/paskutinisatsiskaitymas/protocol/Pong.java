package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Server liveness reply carrying the exact client correlation ID. */
public record Pong(String requestId) implements Message {
    public Pong {
        requestId = WireText.require(requestId, "requestId", 64);
    }
}
