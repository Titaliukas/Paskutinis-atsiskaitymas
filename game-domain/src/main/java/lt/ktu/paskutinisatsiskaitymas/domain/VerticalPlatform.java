package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;

/** Moves at constant speed between its initial Y and endY, reversing at both endpoints. */
public final class VerticalPlatform extends Platform {
    private final PlatformMotion motion;

    public VerticalPlatform(UUID id, Position position, double width, double height, double endY, double speed) {
        this(id, position, width, height, Kind.STANDARD, endY, speed);
    }

    public VerticalPlatform(UUID id, Position position, double width, double height, Kind kind,
            double endY, double speed) {
        this(id, position, width, height, kind, new PlatformMotion(position.y(), endY, speed));
    }

    private VerticalPlatform(UUID id, Position position, double width, double height, Kind kind,
            PlatformMotion motion) {
        super(id, position, width, height, kind);
        this.motion = motion;
    }

    @Override
    protected Position calculateNextPosition(double seconds) {
        return new Position(position().x(), motion.advance(seconds));
    }

    @Override
    protected Position pathStart() { return new Position(position().x(), motion.start()); }

    @Override
    protected Position pathEnd() { return new Position(position().x(), motion.end()); }

    @Override
    protected Platform copy(Position newPosition) {
        return new VerticalPlatform(UUID.randomUUID(), newPosition, width(), height(), kind(),
                motion.translated(newPosition.y() - position().y()));
    }
}
