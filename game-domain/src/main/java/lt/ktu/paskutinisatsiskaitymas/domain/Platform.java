package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** Axis-aligned solid platform in world coordinates. */
public final class Platform implements Prototype<Platform> {
    private final UUID id;
    private Position position;
    private final double width;
    private final double height;

    public Platform(UUID id, Position position, double width, double height) {
        this.id = Objects.requireNonNull(id, "id");
        this.position = Objects.requireNonNull(position, "position");
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Platform dimensions must be finite and positive");
        }
        this.width = width;
        this.height = height;
    }

    public UUID id() { return id; }
    public Position position() { return position; }
    public double width() { return width; }
    public double height() { return height; }

    @Override
    public Platform clone() {
        return new Platform(UUID.randomUUID(), position, width, height);
    }

    /** Copies this platform to a new location without moving the original. */
    public Platform copyAt(Position newPosition) {
        Objects.requireNonNull(newPosition, "newPosition");
        Platform copy = clone();
        copy.position = newPosition;
        return copy;
    }

    public double left() { return position.x(); }
    public double top() { return position.y(); }
    public double right() { return position.x() + width; }
}
