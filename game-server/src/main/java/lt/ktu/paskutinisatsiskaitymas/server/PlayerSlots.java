package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.GameConstants;

/** Synchronizes transport slot reservations; it never mutates the game world. */
final class PlayerSlots {
    private final Map<UUID, Assignment> byConnection = new HashMap<>();

    synchronized Optional<Assignment> reserve(UUID connectionId) {
        if (byConnection.containsKey(connectionId)) {
            return Optional.of(byConnection.get(connectionId));
        }
        for (int slot = 0; slot < GameConstants.MAX_PLAYERS; slot++) {
            int candidate = slot;
            boolean occupied = byConnection.values().stream().anyMatch(value -> value.slot() == candidate);
            if (!occupied) {
                Assignment assignment = new Assignment(UUID.randomUUID(), slot);
                byConnection.put(connectionId, assignment);
                return Optional.of(assignment);
            }
        }
        return Optional.empty();
    }

    synchronized Optional<Assignment> release(UUID connectionId) {
        return Optional.ofNullable(byConnection.remove(connectionId));
    }

    record Assignment(UUID playerId, int slot) { }
}
