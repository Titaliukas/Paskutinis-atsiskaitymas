package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Creates a waiting room and reserves the host's slot. */
public record CreateLobby(String nickname) implements Message {
    public CreateLobby {
        nickname = WireText.require(nickname, "nickname", 32);
    }
}
