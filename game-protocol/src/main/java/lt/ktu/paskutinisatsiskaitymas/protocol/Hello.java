package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Client request to occupy one of the four game slots; nickname is not authentication. */
public record Hello(String nickname) implements Message {
    public Hello {
        nickname = WireText.require(nickname, "nickname", 32);
    }
}
