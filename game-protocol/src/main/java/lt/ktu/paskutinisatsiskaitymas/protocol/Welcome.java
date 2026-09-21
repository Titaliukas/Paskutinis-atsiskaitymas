package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Objects;
import java.util.UUID;

/** Server acknowledgement with a transport-only connection ID, never a domain player ID. */
public record Welcome(UUID connectionId, String nickname) implements Message {
    public Welcome {
        Objects.requireNonNull(connectionId, "connectionId");
        nickname = WireText.require(nickname, "nickname", 32);
    }
}
