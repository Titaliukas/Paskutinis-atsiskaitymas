package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Objects;
import lt.ktu.paskutinisatsiskaitymas.domain.GameSession;

/**
 * Boundary for future match use cases, owned exclusively by the game-loop thread.
 * Construct only when a later slice deliberately creates a world; the handshake never creates one.
 */
public final class MatchController {
    private final GameSession session;

    public MatchController(GameSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public GameSession session() {
        return session;
    }
}
