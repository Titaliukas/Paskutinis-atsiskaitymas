package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Objects;
import java.util.UUID;

/** Successful join acknowledgement with the server-assigned player identity and visual slot. */
public record Welcome(UUID connectionId, UUID playerId, int slot, String nickname) implements Message {
    public Welcome {
        Objects.requireNonNull(connectionId, "connectionId");
        Objects.requireNonNull(playerId, "playerId");
        if (slot < 0 || slot > 3) {
            throw new IllegalArgumentException("Player slot must be between 0 and 3");
        }
        nickname = WireText.require(nickname, "nickname", 32);
    }
}
