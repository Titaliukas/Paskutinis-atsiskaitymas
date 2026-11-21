package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Objects;
import java.util.UUID;

public record LobbyMember(UUID playerId, int slot, String nickname, String character) {
    public LobbyMember {
        Objects.requireNonNull(playerId, "playerId");
        if (slot < 0 || slot > 3) {
            throw new IllegalArgumentException("Invalid lobby slot");
        }
        nickname = WireText.require(nickname, "nickname", 32);
        if (character != null && !SelectCharacter.CHARACTERS.contains(character)) {
            throw new IllegalArgumentException("Unknown character");
        }
    }
}
