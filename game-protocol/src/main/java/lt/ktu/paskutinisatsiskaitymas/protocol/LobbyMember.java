package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Objects;
import java.util.UUID;

public record LobbyMember(UUID playerId, int slot, String nickname, String character) {
    public LobbyMember {
        Objects.requireNonNull(playerId, "playerId");
        if (slot < 0 || slot > 3) {
            throw new IllegalArgumentException("Invalid lobby slot");
        }
        CreateLobby selection = new CreateLobby(nickname, character);
        nickname = selection.nickname();
        character = selection.character();
    }
}
