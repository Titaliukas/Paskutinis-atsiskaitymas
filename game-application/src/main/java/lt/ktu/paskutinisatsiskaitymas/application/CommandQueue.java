package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Optional;

/**
 * Handoff contract from transport adapters to the future game-loop thread.
 * Implementations must be thread-safe and bounded; offer returns false when full.
 * C will be an application-owned command type, never a JSON DTO or a WebSocket reference.
 * Only the game-loop owner may poll. No gameplay command types are introduced in this slice.
 */
public interface CommandQueue<C> {
    boolean offer(C command);
    Optional<C> poll();
}
