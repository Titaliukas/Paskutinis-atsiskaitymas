package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Optional;

/**
 * Handoff contract from transport adapters to the game-loop thread.
 * Implementations must be thread-safe and bounded; offer returns false when full.
 * C is an application-owned command type, never a JSON DTO or a WebSocket reference.
 * Only the game-loop owner may poll.
 */
public interface CommandQueue<C> {
    boolean offer(C command);
    Optional<C> poll();
}
