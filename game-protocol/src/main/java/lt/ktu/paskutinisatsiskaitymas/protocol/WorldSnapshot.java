package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.Objects;

/** Authoritative state broadcast. It contains transport DTOs rather than domain entities. */
public record WorldSnapshot(long tick, ArenaSnapshot arena, List<PlayerSnapshot> players) implements Message {
    public WorldSnapshot {
        if (tick < 0) {
            throw new IllegalArgumentException("Snapshot tick cannot be negative");
        }
        Objects.requireNonNull(arena, "arena");
        players = List.copyOf(players);
        if (players.size() > 2) {
            throw new IllegalArgumentException("A snapshot can contain at most two players");
        }
    }
}
