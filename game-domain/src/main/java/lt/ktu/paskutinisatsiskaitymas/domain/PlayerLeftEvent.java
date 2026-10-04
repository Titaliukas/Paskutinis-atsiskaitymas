package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;
import java.util.UUID;

public record PlayerLeftEvent(UUID playerId, String nickname) implements GameEvent {
    public PlayerLeftEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(nickname, "nickname");
    }
}