package lt.ktu.paskutinisatsiskaitymas.client;

/** A fixed point that keeps emitting one kind of particle at a steady rate, for stress testing. */
final class ParticleSpawner {
    private static final double JITTER = 14.0;

    private final ParticleKind kind;
    private final double x;
    private final double y;
    private final double ratePerSecond;
    private final int direction;
    private double carry;

    ParticleSpawner(ParticleKind kind, double x, double y, double ratePerSecond, int direction) {
        if (ratePerSecond <= 0) {
            throw new IllegalArgumentException("ratePerSecond must be positive");
        }
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.ratePerSecond = ratePerSecond;
        this.direction = direction;
    }

    ParticleKind kind() {
        return kind;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    /** Emits the whole particles owed for {@code seconds}, carrying the fraction over; returns how many. */
    int emit(ParticleSystem system, double seconds) {
        carry += ratePerSecond * seconds;
        int whole = (int) carry;
        carry -= whole;
        if (whole > 0) {
            system.emitBurst(kind, x, y, whole, direction, JITTER);
        }
        return whole;
    }
}
