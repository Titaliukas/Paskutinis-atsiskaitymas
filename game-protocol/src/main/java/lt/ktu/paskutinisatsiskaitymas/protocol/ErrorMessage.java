package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Transport/protocol rejection, not a game-rule outcome. */
public record ErrorMessage(String code, String message) implements Message {
    public ErrorMessage {
        code = WireText.require(code, "code", 64);
        message = WireText.require(message, "message", 256);
    }
}
