package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.Set;

/** Immutable arena dimensions and renderable platform geometry. */
public record ArenaSnapshot(String arenaType, double width, double height, List<PlatformSnapshot> platforms) {
    public ArenaSnapshot {
        if (arenaType == null || !Set.of("combat", "training").contains(arenaType)) {
            throw new IllegalArgumentException("arenaType must be combat or training");
        }
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
        platforms = List.copyOf(platforms);
    }
}
