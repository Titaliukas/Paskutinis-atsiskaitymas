package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.UUID;

/** Detached authoritative phase and bounded pre-hit feedback, independent of live targets. */
public record ExaminatorState(UUID ownerId, Position position, String phase, double cooldownRemaining,
        double phaseRemaining, double simulationTime, long shotSequence, Position emitter, Position aim,
        List<ExaminatorShot> shots) {
    public ExaminatorState { shots = List.copyOf(shots); }
}
