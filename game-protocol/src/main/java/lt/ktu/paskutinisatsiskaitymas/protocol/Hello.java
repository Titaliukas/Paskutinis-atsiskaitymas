package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Client introduction; a nickname does not authenticate a user or create a world entity. */
public record Hello(String nickname) implements Message {
    public Hello {
        nickname = WireText.require(nickname, "nickname", 32);
    }
}
