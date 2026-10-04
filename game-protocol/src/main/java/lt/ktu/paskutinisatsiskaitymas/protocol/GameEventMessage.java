package lt.ktu.paskutinisatsiskaitymas.protocol;

public record GameEventMessage(String kind, String text) implements Message {
    public GameEventMessage {
        kind = WireText.require(kind, "kind", 32);
        text = WireText.require(text, "text", 128);
    }
}