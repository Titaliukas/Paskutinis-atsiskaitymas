package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.UUID;

/** Transport-only state. Access is guarded by MessageRouter's per-session lock. */
final class ConnectionSession {
    final UUID id = UUID.randomUUID();
    String nickname;
}
