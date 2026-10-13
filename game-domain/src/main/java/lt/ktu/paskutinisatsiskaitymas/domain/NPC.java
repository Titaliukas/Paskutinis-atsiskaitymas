package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;

/** Strategy context and precise authoritative body, confined to the session owner thread. */
public final class NPC {
    private final Integer id;
    private Integer targetID;
    private Integer position_x;
    private Integer position_y;
    // STRATEGY-REPORT R2 BEGIN: The context holds exactly one active algorithm through its interface.
    private NPCActivityStrategy activityStrategy;
    // STRATEGY-REPORT R2 END
    private final double attackRange;
    private final double attackDamage;
    private double attackCooldownRemaining;
    private final double attackCooldownSeconds;
    private final double width;
    private final double height;
    private final double speed;
    private double preciseX;
    private double preciseY;
    private double velocityX;
    private double velocityY;
    private boolean grounded;
    private boolean alive = true;
    private boolean facingRight = true;
    private boolean facingTarget;
    private long attackAttemptSequence;

    public NPC(Integer id, Position spawn, double width, double height, double speed,
            double attackRange, double attackDamage, double attackCooldownSeconds,
            NPCActivityStrategy activityStrategy) {
        this.id = NPCGeometry.id(Objects.requireNonNull(id, "id"));
        this.preciseX = Objects.requireNonNull(spawn, "spawn").x();
        this.preciseY = spawn.y();
        this.width = NPCGeometry.positive(width, "width");
        this.height = NPCGeometry.positive(height, "height");
        this.speed = NPCGeometry.positive(speed, "speed");
        this.attackRange = NPCGeometry.positive(attackRange, "attackRange");
        this.attackDamage = NPCGeometry.positive(attackDamage, "attackDamage");
        this.attackCooldownSeconds = NPCGeometry.positive(attackCooldownSeconds, "attackCooldownSeconds");
        updateIntegerViews();
        setActivityStrategy(activityStrategy);
    }

    // STRATEGY-REPORT R2 BEGIN: Runtime replacement changes behavior without replacing the NPC.
    public void setActivityStrategy(NPCActivityStrategy strategy) {
        activityStrategy = Objects.requireNonNull(strategy, "strategy");
    }

    public NPCAction advance(Arena arena, NPCTarget target, double seconds) {
        Objects.requireNonNull(arena, "arena");
        NPCGeometry.positive(seconds, "seconds");
        if (width > arena.width() || height > arena.height()
                || arena.width() > Integer.MAX_VALUE || arena.height() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("NPC body/arena must fit Integer coordinate views");
        }
        attackCooldownRemaining = Math.max(0, attackCooldownRemaining - seconds);
        if (attackCooldownRemaining < 0.000_000_001) {
            attackCooldownRemaining = 0;
        }
        NPCAction action = Objects.requireNonNull(activityStrategy.decide(state(), target, seconds), "decision");
        // STRATEGY-REPORT R2 END
        integrate(action, arena, seconds);
        return action;
    }

    private void integrate(NPCAction action, Arena arena, double seconds) {
        double nextVelocityX = alive ? action.velocityX() : 0;
        double nextVelocityY = alive ? action.velocityY() + GameConstants.GRAVITY * seconds : 0;
        NPCGeometry.finite(nextVelocityY, "integrated velocityY");
        double nextX = NPCGeometry.clamp(NPCGeometry.finite(preciseX + nextVelocityX * seconds, "integrated x"),
                0, arena.width() - width);
        double nextY = NPCGeometry.finite(preciseY + nextVelocityY * seconds, "integrated y");
        double previousBottom = preciseY + height;
        boolean nextGrounded = false;
        if (nextVelocityY >= 0) {
            for (Platform platform : arena.platforms()) {
                if (previousBottom <= platform.top() + 0.000_001 && nextY + height >= platform.top()
                        && nextX + width > platform.left() && nextX < platform.right()) {
                    nextY = Math.min(nextY, platform.top() - height);
                    nextVelocityY = 0;
                    nextGrounded = true;
                }
            }
        }
        double boundedY = NPCGeometry.clamp(nextY, 0, arena.height() - height);
        if (boundedY != nextY) {
            nextVelocityY = 0;
            nextGrounded = boundedY == arena.height() - height;
        }
        preciseX = nextX;
        preciseY = boundedY;
        velocityX = nextVelocityX;
        velocityY = nextVelocityY;
        grounded = nextGrounded;
        if (!facingTarget && velocityX != 0) {
            facingRight = velocityX > 0;
        }
        updateIntegerViews();
    }

    /** Called once by the resolver for an accepted in-range attempt, including a shield block. */
    public void recordAttackAttempt() {
        if (attackCooldownRemaining > 0) {
            throw new IllegalStateException("Attack cooldown is not ready");
        }
        attackAttemptSequence = Math.incrementExact(attackAttemptSequence);
        attackCooldownRemaining = attackCooldownSeconds;
    }

    public NPCState state() {
        return new NPCState(id, position_x, position_y, width, height, speed, velocityX, velocityY,
                alive, targetID, attackRange, attackCooldownRemaining == 0, preciseX, preciseY,
                grounded, facingRight, attackAttemptSequence);
    }

    void retire() {
        alive = false;
        targetID = null;
        velocityX = 0;
        velocityY = 0;
    }

    double attackDamage() { return attackDamage; }

    void setTargetId(Integer value) { targetID = NPCGeometry.id(value); }

    void faceTarget(boolean value, NPCTarget target) {
        facingTarget = value;
        if (value && target != null) {
            double direction = NPCGeometry.direction(state(), target);
            if (direction != 0) {
                facingRight = direction > 0;
            }
        }
    }

    private void updateIntegerViews() {
        if (preciseX < 0 || preciseY < 0 || preciseX > Integer.MAX_VALUE || preciseY > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("NPC coordinates must fit nonnegative Integer views");
        }
        position_x = (int) Math.round(preciseX);
        position_y = (int) Math.round(preciseY);
    }
}
