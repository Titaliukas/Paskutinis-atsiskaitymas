package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** Axis-aligned solid platform in world coordinates. */
public record Platform(UUID id, Position position, double width, double height) {
    public Platform {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Platform dimensions must be finite and positive");
        }
    }

    public double left() { return position.x(); }
    public double top() { return position.y(); }
    public double right() { return position.x() + width; }
}
