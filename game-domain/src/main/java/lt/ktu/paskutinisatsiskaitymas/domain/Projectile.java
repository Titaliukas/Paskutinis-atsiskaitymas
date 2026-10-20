package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** Loop-owned moving projectile. Cloning creates a fresh identity and independent flight state. */
public final class Projectile implements Prototype<Projectile> {
    private final UUID id;
    private final int sourceNPCId;
    private final int targetId;
    private final ProjectileType type;
    private Position position;
    private double velocityX;
    private double velocityY;
    private double remainingSeconds;
    private boolean returning;

    Projectile(UUID id, int sourceNPCId, int targetId, ProjectileType type, Position position,
            double velocityX, double velocityY, double remainingSeconds, boolean returning) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceNPCId = NPCGeometry.id(sourceNPCId);
        this.targetId = NPCGeometry.id(targetId);
        this.type = Objects.requireNonNull(type, "type");
        this.position = Objects.requireNonNull(position, "position");
        this.velocityX = NPCGeometry.finite(velocityX, "velocityX");
        this.velocityY = NPCGeometry.finite(velocityY, "velocityY");
        this.remainingSeconds = NPCGeometry.positive(remainingSeconds, "remainingSeconds");
        this.returning = returning;
    }

    @Override
    public Projectile clone() {
        return new Projectile(UUID.randomUUID(), sourceNPCId, targetId, type, position,
                velocityX, velocityY, remainingSeconds, returning);
    }

    void aimAt(double x, double y) {
        double dx = x - position.x();
        double dy = y - position.y();
        double distance = Math.hypot(dx, dy);
        velocityX = distance == 0 ? GameConstants.NPC_PROJECTILE_SPEED : dx / distance * GameConstants.NPC_PROJECTILE_SPEED;
        velocityY = distance == 0 ? 0 : dy / distance * GameConstants.NPC_PROJECTILE_SPEED;
    }

    Projectile returnToward(NPCState source) {
        Projectile copy = clone();
        copy.returning = true;
        copy.aimAt(source.preciseX() + source.width() / 2, source.preciseY() + source.height() / 2);
        return copy;
    }

    void move(double seconds) {
        position = new Position(position.x() + velocityX * seconds, position.y() + velocityY * seconds);
        remainingSeconds = Math.max(0, remainingSeconds - seconds);
    }

    public ProjectileState state() {
        return new ProjectileState(id, sourceNPCId, targetId, type, position, velocityX, velocityY,
                remainingSeconds, returning);
    }
}
