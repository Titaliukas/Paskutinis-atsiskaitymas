package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;

/** Moves at constant speed between its initial X and endX, reversing at both endpoints. */
public final class HorizontalPlatform extends Platform {
    private final PlatformMotion motion;

    public HorizontalPlatform(UUID id, Position position, double width, double height, double endX, double speed) {
        this(id, position, width, height, Kind.STANDARD, endX, speed);
    }

    public HorizontalPlatform(UUID id, Position position, double width, double height, Kind kind,
            double endX, double speed) {
        this(id, position, width, height, kind, new PlatformMotion(position.x(), endX, speed));
    }

    private HorizontalPlatform(UUID id, Position position, double width, double height, Kind kind,
            PlatformMotion motion) {
        super(id, position, width, height, kind);
        this.motion = motion;
    }

    @Override
    protected Position calculateNextPosition(double seconds) {
        return new Position(motion.advance(seconds), position().y());
    }

    @Override
    protected Position pathStart() { return new Position(motion.start(), position().y()); }

    @Override
    protected Position pathEnd() { return new Position(motion.end(), position().y()); }

    @Override
    protected Platform copy(Position newPosition) {
        return new HorizontalPlatform(UUID.randomUUID(), newPosition, width(), height(), kind(),
                motion.translated(newPosition.x() - position().x()));
    }
}
