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
        boolean grounded,
        boolean shielded,
        double velocityX,
        double stressLevel) {
    public PlayerSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        if (slot < 0 || slot > 3) {
            throw new IllegalArgumentException("Player slot must be between 0 and 3");
        }
        nickname = WireText.require(nickname, "nickname", 32);
        WireNumbers.finite(x, "x");
        WireNumbers.finite(y, "y");
        WireNumbers.positive(width, "width");
        WireNumbers.positive(height, "height");
        WireNumbers.finite(velocityX, "velocityX");
        if (WireNumbers.finite(stressLevel, "stressLevel") < 0) {
            throw new IllegalArgumentException("Stress cannot be negative");
        }
    }
}
