package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;
import java.util.UUID;

/** Server-owned mutable character. It accepts input intent and resolves movement inside domain bounds. */
public final class GameCharacter {
    private static final double COLLISION_EPSILON = 0.000_001;
    private final UUID id;
    private final Player player;
    private final int slot;
    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private boolean grounded;
    private double speedMultiplier = 1.0;
    private double speedMultiplierRemaining;
    private double jumpMultiplier = 1.0;
    private double jumpMultiplierRemaining;
    private boolean shielded;
    private double shieldRemaining;

    GameCharacter(UUID id, Player player, int slot, Position spawn, boolean grounded) {
        this.id = Objects.requireNonNull(id, "id");
        this.player = Objects.requireNonNull(player, "player");
        this.slot = slot;
        this.x = Objects.requireNonNull(spawn, "spawn").x();
        this.y = spawn.y();
        this.grounded = grounded;
    }

    void advance(MovementInput input, Arena arena, double seconds) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(arena, "arena");
        if (!Double.isFinite(seconds) || seconds <= 0) {
            throw new IllegalArgumentException("Tick duration must be finite and positive");
        }

        decayItemEffects(seconds);

        int direction = (input.right() ? 1 : 0) - (input.left() ? 1 : 0);
        velocityX = direction * GameConstants.MOVE_SPEED * speedMultiplier;
        if (input.jumpRequested() && grounded) {
            velocityY = -GameConstants.JUMP_SPEED * Math.sqrt(jumpMultiplier);
            grounded = false;
        }

        double previousBottom = y + GameConstants.PLAYER_HEIGHT;
        velocityY += GameConstants.GRAVITY * seconds;
        x = clamp(x + velocityX * seconds, 0, arena.width() - GameConstants.PLAYER_WIDTH);
        double nextY = y + velocityY * seconds;
        grounded = false;

        if (velocityY >= 0) {
            for (Platform platform : arena.platforms()) {
                double nextBottom = nextY + GameConstants.PLAYER_HEIGHT;
                boolean crossesTop = previousBottom <= platform.top() + COLLISION_EPSILON
                        && nextBottom >= platform.top();
                boolean overlapsHorizontally = x + GameConstants.PLAYER_WIDTH > platform.left()
                        && x < platform.right();
                if (crossesTop && overlapsHorizontally) {
                    nextY = platform.top() - GameConstants.PLAYER_HEIGHT;
                    velocityY = 0;
                    grounded = true;
                    break;
                }
            }
        }

        if (nextY < 0) {
            nextY = 0;
            velocityY = Math.max(0, velocityY);
        }
        double bottomBoundary = arena.height() - GameConstants.PLAYER_HEIGHT;
        if (nextY > bottomBoundary) {
            nextY = bottomBoundary;
            velocityY = 0;
            grounded = true;
        }
        y = nextY;
    }

    void applySpeedMultiplier(double multiplier, double seconds) {
        speedMultiplier = multiplier;
        speedMultiplierRemaining = seconds;
    }

    void applyJumpMultiplier(double multiplier, double seconds) {
        jumpMultiplier = multiplier;
        jumpMultiplierRemaining = seconds;
    }

    void applyShield(double seconds) {
        shielded = true;
        shieldRemaining = seconds;
    }

    boolean shielded() {
        return shielded;
    }

    boolean overlaps(Position point, double radius) {
        Objects.requireNonNull(point, "point");
        double closestX = clamp(point.x(), x, x + GameConstants.PLAYER_WIDTH);
        double closestY = clamp(point.y(), y, y + GameConstants.PLAYER_HEIGHT);
        double dx = point.x() - closestX;
        double dy = point.y() - closestY;
        return (dx * dx + dy * dy) <= radius * radius;
    }

    private void decayItemEffects(double seconds) {
        if (speedMultiplierRemaining > 0) {
            speedMultiplierRemaining -= seconds;
            if (speedMultiplierRemaining <= 0) {
                speedMultiplierRemaining = 0;
                speedMultiplier = 1.0;
            }
        }

        if (jumpMultiplierRemaining > 0) {
            jumpMultiplierRemaining -= seconds;
            if (jumpMultiplierRemaining <= 0) {
                jumpMultiplierRemaining = 0;
                jumpMultiplier = 1.0;
            }
        }

        if (shieldRemaining > 0) {
            shieldRemaining -= seconds;
            if (shieldRemaining <= 0) {
                shieldRemaining = 0;
                shielded = false;
            }
        }
    }

    GameCharacterState state() {
        return new GameCharacterState(player.id(), slot, player.nickname(), x, y,
                velocityX, velocityY, grounded, shielded);
    }

    UUID id() {
        return id;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
