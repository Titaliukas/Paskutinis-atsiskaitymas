package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Transport-only projectile geometry; type names are an explicit wire vocabulary. */
public record ProjectileSnapshot(UUID id, int sourceNPCId, String projectileType, double x, double y,
        double radius, double velocityX, double velocityY, boolean returning) {
    private static final Set<String> TYPES = Set.of("BOOK", "DIPLOMA");

    public ProjectileSnapshot {
        Objects.requireNonNull(id, "id");
        if (sourceNPCId <= 0) {
            throw new IllegalArgumentException("Projectile source identity must be positive");
        }
        if (!TYPES.contains(WireText.require(projectileType, "projectileType", 16))) {
            throw new IllegalArgumentException("Unknown projectile type");
        }
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.positive(radius, "radius");
        WireNumbers.finite(x - radius, "left");
        WireNumbers.finite(x + radius, "right");
        WireNumbers.finite(y - radius, "top");
        WireNumbers.finite(y + radius, "bottom");
        WireNumbers.finite(velocityX, "velocityX");
        WireNumbers.finite(velocityY, "velocityY");
    }
}
