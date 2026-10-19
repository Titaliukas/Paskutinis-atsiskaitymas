package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Objects;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.CharacterType;

/** Adds a server-assigned player to a reserved slot with the character the player chose. */
public record JoinPlayerCommand(UUID playerId, int slot, String nickname,
        CharacterType characterType) implements GameCommand {
    public JoinPlayerCommand {
        Objects.requireNonNull(playerId, "playerId");
        if (slot < 0 || slot > 3) {
            throw new IllegalArgumentException("Player slot must be between 0 and 3");
        }
        if (nickname == null || nickname.isBlank() || nickname.length() > 32) {
            throw new IllegalArgumentException("Nickname must contain 1 to 32 characters");
        }
        Objects.requireNonNull(characterType, "characterType");
    }
}