package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Objects;
import java.util.UUID;

/** Immutable render state for one player rectangle. */
public record PlayerSnapshot(
        UUID playerId,
        int slot,
        String nickname,
        double x,
        double y,
        double width,
        double height,
        boolean grounded) {
    public PlayerSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        if (slot < 0 || slot > 1) {
            throw new IllegalArgumentException("Player slot must be 0 or 1");
        }
        nickname = WireText.require(nickname, "nickname", 32);
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
    }
}
