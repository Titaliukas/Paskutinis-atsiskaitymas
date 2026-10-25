package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;

/** Immutable recipe; patrol boundaries constrain the body's left edge, including its full width. */
public record NPCSpawn(Integer id, Position position, double leftBoundary, double rightBoundary) {
    public NPCSpawn {
        NPCGeometry.id(Objects.requireNonNull(id, "id"));
        Objects.requireNonNull(position, "position");
        NPCGeometry.finite(leftBoundary, "leftBoundary");
        NPCGeometry.finite(rightBoundary, "rightBoundary");
        if (leftBoundary < 0 || rightBoundary <= leftBoundary || position.x() < leftBoundary || position.x() > rightBoundary
                || position.y() < 0) {
            throw new IllegalArgumentException("NPC spawn must lie inside its patrol interval");
        }
    }
}
