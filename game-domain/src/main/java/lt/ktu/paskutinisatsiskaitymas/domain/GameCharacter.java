package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;
import java.util.UUID;

/** Server-owned mutable character. It accepts input intent and resolves movement inside domain bounds. */
public final class GameCharacter {
    private static final double COLLISION_EPSILON = 0.000_001;
    private static final double KNOCKBACK_DECAY_PER_SECOND = 6.0;
    private final UUID id;
    private final Player player;
    private final int slot;
    private final CharacterType characterType;
    private final Position spawn;
    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private boolean grounded;
    /** Decorator chain of active timed effects; BaseStats when none are active. */
    private CharacterStats stats = BaseStats.INSTANCE;
    private double stressLevel;
    private double slowRemaining;
    private int health = GameConstants.MAX_HEALTH;
    private int facing;
    private double knockbackX;
    private double rangedCooldownRemaining;
    private double meleeCooldownRemaining;

    GameCharacter(UUID id, Player player, int slot, Position spawn, boolean grounded, CharacterType characterType) {
        this.id = Objects.requireNonNull(id, "id");
        this.player = Objects.requireNonNull(player, "player");
        this.slot = slot;
        this.characterType = Objects.requireNonNull(characterType, "characterType");
        this.spawn = Objects.requireNonNull(spawn, "spawn");
        this.facing = slot % 2 == 0 ? 1 : -1;
        this.x = spawn.x();
        this.y = spawn.y();
        this.grounded = grounded;
    }
    
    void advance(MovementInput input, Arena arena, double seconds) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(arena, "arena");
        if (!Double.isFinite(seconds) || seconds <= 0) {
            throw new IllegalArgumentException("Tick duration must be finite and positive");
        }

        stats = stats.tick(seconds);
        decayCooldowns(seconds);
        slowRemaining = Math.max(0, slowRemaining - seconds);
        if (slowRemaining < 0.000_000_001) {
            slowRemaining = 0;
        }

        int direction = (input.right() ? 1 : 0) - (input.left() ? 1 : 0);
        if (direction != 0) {
            facing = direction;
        }

        velocityX = direction * GameConstants.MOVE_SPEED * stats.speedMultiplier()
                * (slowRemaining > 0 ? GameConstants.PROJECTILE_SLOW_MULTIPLIER : 1.0);
        if (input.jumpRequested() && grounded) {
            velocityY = -GameConstants.JUMP_SPEED * Math.sqrt(stats.jumpMultiplier());
            grounded = false;
        }

        double previousBottom = y + GameConstants.PLAYER_HEIGHT;
        velocityY += GameConstants.GRAVITY * seconds;
        x = clamp(x + (velocityX + knockbackX) * seconds, 0, arena.width() - GameConstants.PLAYER_WIDTH);
        knockbackX *= Math.max(0.0, 1.0 - KNOCKBACK_DECAY_PER_SECOND * seconds);
        if (Math.abs(knockbackX) < 1.0) {
            knockbackX = 0;
        }
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

    // Each effect wraps the chain in a new layer. An existing layer of the same type is removed first, so picking up
    // the same effect again refreshes its timer, while different effects (for example boost and slow) stack.

    void applySpeedBoost(double multiplier, double seconds) {
        stats = new SpeedBoostDecorator(stats.without(SpeedBoostDecorator.class), multiplier, seconds);
    }

    void applySlow(double multiplier, double seconds) {
        stats = new SlowDecorator(stats.without(SlowDecorator.class), multiplier, seconds);
    }

    void applyJumpBoost(double multiplier, double seconds) {
        stats = new JumpBoostDecorator(stats.without(JumpBoostDecorator.class), multiplier, seconds);
    }

    void applyShield(double seconds) {
        stats = new ShieldDecorator(stats.without(ShieldDecorator.class), seconds);
    }

    CharacterStats stats() {
        return stats;
    }

    /** Single damage entry point: a shield blocks it, otherwise stress grows and health drops. */
    public boolean takeDamage(double damage) {
        NPCGeometry.positive(damage, "damage");
        if (stats.shielded()) {
            return false;
        }
        double nextStress = NPCGeometry.finite(stressLevel + damage, "stressLevel");
        if (nextStress == stressLevel) {
            return false; // nothing actually changed
        }
        stressLevel = nextStress;
        health -= (int) Math.ceil(damage);
        if (health <= 0) {
            respawn();
        }
        return true;
    }

    boolean shielded() {
        return stats.shielded();
    }

     /** Replaces any previous knockback; velocityY is set directly so a negative value launches upward. */
    void applyImpulse(double impulseX, double impulseY) {
        knockbackX = impulseX;
        velocityY = impulseY;
        grounded = false;
    }

    /** Starts the cooldown and returns true only if the attack type is ready. */
    boolean tryUseCooldown(AttackType type) {
        if (type == AttackType.RANGED) {
            if (rangedCooldownRemaining > 0) {
                return false;
            }
            rangedCooldownRemaining = GameConstants.RANGED_COOLDOWN_SECONDS;
        } else {
            if (meleeCooldownRemaining > 0) {
                return false;
            }
            meleeCooldownRemaining = GameConstants.MELEE_COOLDOWN_SECONDS;
        }
        return true;
    }

    private void respawn() {
        health = GameConstants.MAX_HEALTH;
        x = spawn.x();
        y = spawn.y();
        velocityX = 0;
        velocityY = 0;
        knockbackX = 0;
        grounded = true;
        stats = BaseStats.INSTANCE;
        slowRemaining = 0;
    }

    void applyProjectileSlow() {
        if (!shielded()) {
            slowRemaining = GameConstants.PROJECTILE_SLOW_SECONDS;
        }
    }

    boolean overlaps(Position point, double radius) {
        Objects.requireNonNull(point, "point");
        double closestX = clamp(point.x(), x, x + GameConstants.PLAYER_WIDTH);
        double closestY = clamp(point.y(), y, y + GameConstants.PLAYER_HEIGHT);
        double dx = point.x() - closestX;
        double dy = point.y() - closestY;
        return (dx * dx + dy * dy) <= radius * radius;
    }

    boolean overlapsRect(double left, double top, double width, double height) {
        return x < left + width && x + GameConstants.PLAYER_WIDTH > left
            && y < top + height && y + GameConstants.PLAYER_HEIGHT > top;
    }

    private void decayCooldowns(double seconds) {
        rangedCooldownRemaining = Math.max(0, rangedCooldownRemaining - seconds);
        meleeCooldownRemaining = Math.max(0, meleeCooldownRemaining - seconds);
    }

    GameCharacterState state() {
        return new GameCharacterState(player.id(), slot, player.nickname(), x, y,
                velocityX, velocityY, grounded, stats.shielded(), health, stressLevel, characterType, slowRemaining > 0);
    }

    UUID id() {
        return id;
    }

    UUID playerId() {
        return player.id();
    }

    int facing() {
        return facing;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }
    
    CharacterType characterType() {
        return characterType;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
