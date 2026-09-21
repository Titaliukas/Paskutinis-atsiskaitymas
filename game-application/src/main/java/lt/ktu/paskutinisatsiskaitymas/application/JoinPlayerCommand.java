package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Objects;
import java.util.UUID;

/** Adds a server-assigned player to one of the two reserved slots. */
public record JoinPlayerCommand(UUID playerId, int slot, String nickname) implements GameCommand {
    public JoinPlayerCommand {
        Objects.requireNonNull(playerId, "playerId");
        if (slot < 0 || slot > 1) {
            throw new IllegalArgumentException("Player slot must be 0 or 1");
        }
        if (nickname == null || nickname.isBlank() || nickname.length() > 32) {
            throw new IllegalArgumentException("Nickname must contain 1 to 32 characters");
        }
    }
}
