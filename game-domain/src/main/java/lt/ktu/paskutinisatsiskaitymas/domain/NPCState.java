package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;

/** Immutable decision snapshot. Integer coordinates are rounded views; geometry uses precise coordinates. */
public record NPCState(Integer id, Integer x, Integer y, double width, double height,
        double speed, double velocityX, double velocityY, boolean alive, Integer targetId,
        double attackRange, boolean attackReady, double preciseX, double preciseY,
        boolean grounded, boolean facingRight, long attackAttemptSequence) {
    public NPCState {
        NPCGeometry.id(Objects.requireNonNull(id, "id"));
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        NPCGeometry.id(targetId);
        NPCGeometry.positive(width, "width");
        NPCGeometry.positive(height, "height");
        NPCGeometry.positive(speed, "speed");
        NPCGeometry.positive(attackRange, "attackRange");
        NPCGeometry.finite(velocityX, "velocityX");
        NPCGeometry.finite(velocityY, "velocityY");
        NPCGeometry.finite(preciseX, "preciseX");
        NPCGeometry.finite(preciseY, "preciseY");
        NPCGeometry.finite(preciseX + width, "right");
        NPCGeometry.finite(preciseY + height, "bottom");
        if (attackAttemptSequence < 0 || x != Math.round(preciseX) || y != Math.round(preciseY)) {
            throw new IllegalArgumentException("Invalid sequence or rounded coordinates");
        }
    }
}
