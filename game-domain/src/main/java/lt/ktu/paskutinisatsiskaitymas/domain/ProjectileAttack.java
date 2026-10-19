package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Refined abstraction (ranged, J): flies horizontally and is consumed by the first target it touches. */
public final class ProjectileAttack extends Attack {
    private double x;
    private final double y;
    private double remainingSeconds = GameConstants.PROJECTILE_LIFETIME_SECONDS;

    public ProjectileAttack(UUID id, UUID sourceId, int direction, List<AttackEffect> effects, Position start) {
        super(id, sourceId, direction, effects);
        this.x = start.x();
        this.y = start.y();
    }

    @Override
    boolean update(Map<UUID, GameCharacter> characters, Collection<NPC> npcs, Arena arena, double seconds) {
        x += direction() * GameConstants.PROJECTILE_SPEED * seconds;
        remainingSeconds -= seconds;
        if (remainingSeconds <= 0 || x < 0 || x > arena.width()) {
            return true;
        }
        Position center = new Position(x, y);
        for (GameCharacter character : characters.values()) {
            if (!character.playerId().equals(sourceId())
                    && character.overlaps(center, GameConstants.PROJECTILE_RADIUS)) {
                hit(character);
                return true;
            }
        }
        for (NPC npc : npcs) {
            if (npc.alive() && npc.overlaps(center, GameConstants.PROJECTILE_RADIUS)) {
                hit(npc);
                return true;
            }
        }
        return false;
    }

    @Override
    AttackState state() {
        double r = GameConstants.PROJECTILE_RADIUS;
        return new AttackState(id(), sourceId(), AttackType.RANGED, direction(),
                x - r, y - r, 2 * r, 2 * r);
    }
}