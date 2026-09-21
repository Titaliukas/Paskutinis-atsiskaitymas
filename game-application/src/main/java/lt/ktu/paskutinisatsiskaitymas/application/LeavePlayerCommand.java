package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Objects;
import java.util.UUID;

/** Removes a disconnected player and its retained input state. */
public record LeavePlayerCommand(UUID playerId) implements GameCommand {
    public LeavePlayerCommand {
        Objects.requireNonNull(playerId, "playerId");
    }
}
