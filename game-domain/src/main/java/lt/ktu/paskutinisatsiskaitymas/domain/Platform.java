package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

/** Server-owned one-way surface. Subclasses supply motion; this class owns the update sequence. */
public abstract class Platform implements Prototype<Platform> {
    public enum Kind { STANDARD, SERVICE }
    private final Kind kind;
    private final UUID id;
    private Position position;
    private Position previousPosition;
    private final double width;
    private final double height;
    private double left;
    private double top;
    private double right;
    private double bottom;
    private List<PlatformPassenger> passengers = List.of();

    protected Platform(UUID id, Position position, double width, double height, Kind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.id = Objects.requireNonNull(id, "id");
        this.position = Objects.requireNonNull(position, "position");
        previousPosition = position;
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0
                || !Double.isFinite(position.x() + width) || !Double.isFinite(position.y() + height)) {
            throw new IllegalArgumentException("Platform dimensions and bounds must be finite and positive");
        }
        this.width = width;
        this.height = height;
        updateCollisionBounds();
    }

    public final void update(double seconds) {
        update(seconds, List.of());
    }

    /** Template Method: remember, calculate, refresh collision geometry, then carry assigned bodies. */
    final void update(double seconds, Collection<? extends PlatformPassenger> standingPassengers) {
        NPCGeometry.positive(seconds, "seconds");
        passengers = List.copyOf(standingPassengers);
        previousPosition = position;
        position = Objects.requireNonNull(calculateNextPosition(seconds), "next position");
        updateCollisionBounds();
        double dx = position.x() - previousPosition.x();
        double dy = position.y() - previousPosition.y();
        for (PlatformPassenger passenger : passengers) {
            passenger.moveWithPlatform(dx, dy);
        }
    }

    protected abstract Position calculateNextPosition(double seconds);

    private void updateCollisionBounds() {
        left = position.x();
        top = position.y();
        right = left + width;
        bottom = top + height;
    }

    final boolean supports(PlatformPassenger passenger) {
        return passenger.canRidePlatform() && Math.abs(passenger.platformFeetY() - top) <= 0.000_001
                && passenger.platformX() + passenger.platformWidth() > left && passenger.platformX() < right;
    }

    /** Carried bodies already start at the new top; other bodies cross the platform's swept top. */
    final boolean crossesTop(PlatformPassenger passenger, double previousBottom, double nextBottom) {
        double previousTop = passengers.contains(passenger) ? top : previousPosition.y();
        return previousBottom <= previousTop + 0.000_001 && nextBottom >= top;
    }

    protected Position pathStart() { return position; }
    protected Position pathEnd() { return position; }

    final boolean fitsInside(double arenaWidth, double arenaHeight) {
        return fitsInside(pathStart(), arenaWidth, arenaHeight) && fitsInside(pathEnd(), arenaWidth, arenaHeight);
    }

    private boolean fitsInside(Position point, double arenaWidth, double arenaHeight) {
        return point.x() >= 0 && point.y() >= 0 && point.x() + width <= arenaWidth
                && point.y() + height <= arenaHeight;
    }

    public final Kind kind() { return kind; }
    public final UUID id() { return id; }
    public final Position position() { return position; }
    public final Position previousPosition() { return previousPosition; }
    public final double width() { return width; }
    public final double height() { return height; }
    public final double left() { return left; }
    public final double top() { return top; }
    public final double right() { return right; }
    public final double bottom() { return bottom; }

    @Override
    public final Platform clone() { return copyAt(position); }

    /** A fresh identity, translated path and independent motion phase; no passengers are copied. */
    public final Platform copyAt(Position newPosition) {
        return copy(Objects.requireNonNull(newPosition, "newPosition"));
    }

    protected abstract Platform copy(Position newPosition);
}
