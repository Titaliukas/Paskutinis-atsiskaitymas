package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.Set;

/** Immutable arena dimensions and renderable platform geometry. */
public record ArenaSnapshot(String arenaType, double width, double height, List<PlatformSnapshot> platforms,
        SidePassageSnapshot sidePassage) {
    public ArenaSnapshot(String arenaType, double width, double height, List<PlatformSnapshot> platforms) {
        this(arenaType, width, height, platforms, null);
    }

    public ArenaSnapshot {
        if (arenaType == null || !Set.of("combat", "training").contains(arenaType)) {
            throw new IllegalArgumentException("arenaType must be combat or training");
        }
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
        platforms = List.copyOf(platforms);
        if (sidePassage != null && (!arenaType.equals("combat") || sidePassage.supportY() > height)) {
            throw new IllegalArgumentException("Passage must be inside a Combat arena");
        }
    }
}
