package lt.ktu.paskutinisatsiskaitymas.domain;

/** Shared scalar ping-pong arithmetic; axis choice remains in the concrete platform. */
final class PlatformMotion {
    private final double start;
    private final double end;
    private final double speed;
    private final double period;
    private double phase;

    PlatformMotion(double start, double end, double speed) {
        this.start = NPCGeometry.finite(start, "start");
        this.end = NPCGeometry.finite(end, "end");
        this.speed = NPCGeometry.positive(speed, "speed");
        period = 2 * (Math.abs(end - start) / speed);
        if (start == end || !Double.isFinite(end - start) || !Double.isFinite(period) || period <= 0) {
            throw new IllegalArgumentException("Movement endpoints must differ and yield a finite positive period");
        }
    }

    double advance(double seconds) {
        // Reduce time first, avoiding overflow and retaining overshoot through any number of reversals.
        phase = (phase + (seconds % period) / period * 2) % 2;
        return start + (end - start) * (phase <= 1 ? phase : 2 - phase);
    }

    PlatformMotion translated(double offset) {
        PlatformMotion copy = new PlatformMotion(start + offset, end + offset, speed);
        copy.phase = phase;
        return copy;
    }

    double start() { return start; }
    double end() { return end; }
}
