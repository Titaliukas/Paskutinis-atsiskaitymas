package lt.ktu.paskutinisatsiskaitymas.protocol;

public record LobbyClosed(String reason) implements Message {
    public LobbyClosed {
        reason = WireText.require(reason, "reason", 256);
    }
}
