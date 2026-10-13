package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;

/** Detached view of an actual current player character; numeric ID is allocated by its session. */
public record NPCTarget(Integer id, double x, double y, double width, double height) {
    public NPCTarget {
        NPCGeometry.id(Objects.requireNonNull(id, "id"));
        NPCGeometry.finite(x, "x");
        NPCGeometry.finite(y, "y");
        NPCGeometry.positive(width, "width");
        NPCGeometry.positive(height, "height");
        NPCGeometry.finite(x + width, "right");
        NPCGeometry.finite(y + height, "bottom");
    }
}
