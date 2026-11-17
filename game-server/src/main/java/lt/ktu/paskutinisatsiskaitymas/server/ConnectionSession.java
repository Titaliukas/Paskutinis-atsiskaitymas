package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import lt.ktu.paskutinisatsiskaitymas.protocol.Message;

/** Mediator colleague. Membership is guarded by ServerLobbyMediator's monitor. */
final class ConnectionSession {
    final UUID id = UUID.randomUUID();
    private final Consumer<Message> outbound;
    UUID playerId;
    int slot = -1;
    String nickname;
    String lobbyCode;
    volatile boolean disconnected;

    ConnectionSession(Consumer<Message> outbound) {
        this.outbound = Objects.requireNonNull(outbound, "outbound");
    }

    void send(Message message) {
        if (!disconnected) {
            outbound.accept(message);
        }
    }

    void clearMembership() {
        playerId = null;
        slot = -1;
        nickname = null;
        lobbyCode = null;
    }
}
