package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Pre-hit coordinates survive removal of the target; sequence is monotonic per owner. */
public record ExaminatorShotSnapshot(long sequence, double originX, double originY, double endpointX,
        double endpointY, double remainingSeconds) {
    public ExaminatorShotSnapshot {
        WireNumbers.finite(originX, "originX");
        WireNumbers.finite(originY, "originY");
        WireNumbers.finite(endpointX, "endpointX");
        WireNumbers.finite(endpointY, "endpointY");
        WireNumbers.finite(remainingSeconds, "remainingSeconds");
        if (sequence <= 0 || remainingSeconds <= 0 || remainingSeconds > 0.2) {
            throw new IllegalArgumentException("Invalid shot feedback");
        }
    }
}
