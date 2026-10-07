package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;
import java.util.UUID;

public record PlayerJoinedEvent(UUID playerId, String nickname) implements GameEvent {
    public PlayerJoinedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(nickname, "nickname");
    }
}