package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Detached authoritative drone state and short-lived, sequenced volley feedback. */
public record ExaminatorSnapshot(UUID ownerId, double x, double y, String phase, double cooldownRemaining,
        double phaseRemaining, long volleySequence, double originX, double originY, List<ShotEndpoint> endpoints) {
    public ExaminatorSnapshot {
        Objects.requireNonNull(ownerId, "ownerId");
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.finite(originX, "originX");
        WireNumbers.finite(originY, "originY");
        if (!List.of("IDLE", "SCAN", "FIRE").contains(Objects.requireNonNull(phase, "phase"))) {
            throw new IllegalArgumentException("Unknown Examinator phase");
        }
        WireNumbers.finite(cooldownRemaining, "cooldownRemaining");
        WireNumbers.finite(phaseRemaining, "phaseRemaining");
        if (cooldownRemaining < 0 || cooldownRemaining > 10 || phaseRemaining < 0
                || phaseRemaining > (phase.equals("SCAN") ? 0.5 : phase.equals("FIRE") ? 0.25 : 0)
                || !phase.equals("IDLE") && phaseRemaining == 0 || volleySequence < 0) {
            throw new IllegalArgumentException("Invalid ability timing or sequence");
        }
        endpoints = List.copyOf(endpoints);
        if (endpoints.size() > 64 || !phase.equals("FIRE") && !endpoints.isEmpty()
                || phase.equals("FIRE") && volleySequence == 0) {
            throw new IllegalArgumentException("Invalid shot feedback");
        }
    }
}
