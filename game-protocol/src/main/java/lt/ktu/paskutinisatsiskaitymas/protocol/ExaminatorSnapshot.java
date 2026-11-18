package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Detached drone state; simulation time also lets clients reject repeated or stale snapshots. */
public record ExaminatorSnapshot(UUID ownerId, double x, double y, String phase, double cooldownRemaining,
        double phaseRemaining, double simulationTime, long shotSequence, double emitterX, double emitterY,
        double aimX, double aimY, List<ExaminatorShotSnapshot> shots) {
    public ExaminatorSnapshot {
        Objects.requireNonNull(ownerId, "ownerId");
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.finite(emitterX, "emitterX");
        WireNumbers.finite(emitterY, "emitterY");
        WireNumbers.finite(aimX, "aimX");
        WireNumbers.finite(aimY, "aimY");
        WireNumbers.finite(simulationTime, "simulationTime");
        double maximum = switch (Objects.requireNonNull(phase, "phase")) {
            case "IDLE" -> 0;
            case "FLY" -> 0.8;
            case "INSPECT" -> 0.6;
            case "ACQUIRE", "FIRE" -> 0.2;
            case "AIM" -> 0.5;
            default -> throw new IllegalArgumentException("Unknown Examinator phase");
        };
        WireNumbers.finite(cooldownRemaining, "cooldownRemaining");
        WireNumbers.finite(phaseRemaining, "phaseRemaining");
        if (cooldownRemaining < 0 || cooldownRemaining > 30 || phaseRemaining < 0 || phaseRemaining > maximum
                || !phase.equals("IDLE") && phaseRemaining == 0 || shotSequence < 0 || simulationTime < 0) {
            throw new IllegalArgumentException("Invalid ability timing or sequence");
        }
        shots = List.copyOf(shots);
        long previous = 0;
        if (shots.size() > 4 || !phase.equals("FIRE") && !shots.isEmpty()
                || phase.equals("FIRE") && shots.isEmpty()) {
            throw new IllegalArgumentException("Invalid shot feedback");
        }
        for (ExaminatorShotSnapshot shot : shots) {
            if (shot.sequence() <= previous || shot.sequence() > shotSequence) {
                throw new IllegalArgumentException("Invalid shot order");
            }
            previous = shot.sequence();
        }
        if (!shots.isEmpty() && previous != shotSequence) {
            throw new IllegalArgumentException("Missing latest shot feedback");
        }
    }
}
