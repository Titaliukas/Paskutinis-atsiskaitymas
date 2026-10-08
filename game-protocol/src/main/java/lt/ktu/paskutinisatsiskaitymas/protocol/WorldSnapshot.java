package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.Objects;

/** Authoritative state broadcast. It contains transport DTOs rather than domain entities. */
public record WorldSnapshot(long tick, ArenaSnapshot arena, List<PlayerSnapshot> players, List<ItemSnapshot> items,
        List<NPCSnapshot> npcs) implements Message {
    public WorldSnapshot {
        if (tick < 0) {
            throw new IllegalArgumentException("Snapshot tick cannot be negative");
        }
        Objects.requireNonNull(arena, "arena");
        players = List.copyOf(players);
        items = List.copyOf(items);
        npcs = List.copyOf(npcs);
        if (npcs.stream().map(NPCSnapshot::id).distinct().count() != npcs.size()) {
            throw new IllegalArgumentException("NPC IDs must be unique");
        }
        if (players.size() > 4) {
            throw new IllegalArgumentException("A snapshot can contain at most four players");
        }
    }
}
