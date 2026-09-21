package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;
import java.util.UUID;

/** Immutable view of a character, suitable for mapping to an outward application result. */
public record GameCharacterState(
        UUID playerId,
        int slot,
        String nickname,
        double x,
        double y,
        double velocityX,
        double velocityY,
        boolean grounded) {
    public GameCharacterState {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(nickname, "nickname");
    }
}
