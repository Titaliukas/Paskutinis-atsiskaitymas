package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** A future moving attack entity, linked by identity; no trajectory or collision behavior. */
public record Projectile(UUID id, UUID attackId, Position position) {
    public Projectile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(attackId, "attackId");
        Objects.requireNonNull(position, "position");
    }
}
