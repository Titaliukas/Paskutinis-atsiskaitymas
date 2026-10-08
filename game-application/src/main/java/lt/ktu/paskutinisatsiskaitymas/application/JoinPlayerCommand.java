package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Objects;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.GameConstants;

/** Adds a server-assigned player to one of the four reserved slots. */
public record JoinPlayerCommand(UUID playerId, int slot, String nickname) implements GameCommand {
    public JoinPlayerCommand {
        Objects.requireNonNull(playerId, "playerId");
        if (slot < 0 || slot >= GameConstants.MAX_PLAYERS) {
            throw new IllegalArgumentException("Player slot must be between 0 and 3");
        }
        if (nickname == null || nickname.isBlank() || nickname.length() > 32) {
            throw new IllegalArgumentException("Nickname must contain 1 to 32 characters");
        }
    }
}
