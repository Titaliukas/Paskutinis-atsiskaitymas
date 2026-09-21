package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;

/** Immutable arena dimensions and renderable platform geometry. */
public record ArenaSnapshot(double width, double height, List<PlatformSnapshot> platforms) {
    public ArenaSnapshot {
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
        platforms = List.copyOf(platforms);
    }
}
