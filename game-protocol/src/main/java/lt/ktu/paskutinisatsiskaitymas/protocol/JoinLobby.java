package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Reserves a slot in an existing waiting room. */
public record JoinLobby(String code, String nickname) implements Message {
    public JoinLobby {
        code = LobbyCode.normalize(code);
        nickname = WireText.require(nickname, "nickname", 32);
    }
}
