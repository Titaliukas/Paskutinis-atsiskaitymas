package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.List;
import java.util.Objects;

/** Future aggregate for one match. Declares membership and arena ownership; has no match lifecycle. */
public record GameSession(UUID id, Arena arena, List<Player> players) {
    public GameSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(arena, "arena");
        players = List.copyOf(players);
    }
}
