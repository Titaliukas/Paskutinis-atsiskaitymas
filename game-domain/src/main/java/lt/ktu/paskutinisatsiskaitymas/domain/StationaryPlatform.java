package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;

/** A platform whose position never changes during an update. */
public final class StationaryPlatform extends Platform {
    public StationaryPlatform(UUID id, Position position, double width, double height) {
        this(id, position, width, height, Kind.STANDARD);
    }

    public StationaryPlatform(UUID id, Position position, double width, double height, Kind kind) {
        super(id, position, width, height, kind);
    }

    @Override
    protected Position calculateNextPosition(double seconds) { return position(); }

    @Override
    protected Platform copy(Position newPosition) {
        return new StationaryPlatform(UUID.randomUUID(), newPosition, width(), height(), kind());
    }
}
