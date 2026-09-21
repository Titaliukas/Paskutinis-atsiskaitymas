package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** A human participant identity, independent of a transport connection or character. */
public record Player(UUID id, String nickname) {
    public Player {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nickname, "nickname");
    }
}
