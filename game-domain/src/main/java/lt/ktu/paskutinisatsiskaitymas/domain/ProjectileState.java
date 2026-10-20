package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;

/** Detached immutable view; mutable projectiles never leave the match aggregate. */
public record ProjectileState(UUID id, int sourceNPCId, int targetId, ProjectileType type, Position position,
        double velocityX, double velocityY, double remainingSeconds, boolean returning) {
}
