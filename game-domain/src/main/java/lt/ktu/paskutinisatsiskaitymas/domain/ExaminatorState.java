package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.UUID;

/** Detached presentation data; endpoints survive the removal or respawn of a struck object. */
public record ExaminatorState(UUID ownerId, Position position, String phase, double cooldownRemaining,
        double phaseRemaining, long volleySequence, Position shotOrigin, List<Position> endpoints) {
    public ExaminatorState { endpoints = List.copyOf(endpoints); }
}
