package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Refined abstraction (melee, K): a short-lived hitbox in front of the attacker that hits each target once. */
public final class MeleeAttack extends Attack {
    private final Set<UUID> alreadyHit = new HashSet<>();
    private final Set<Integer> alreadyHitNpcs = new HashSet<>();
    private double remainingSeconds = GameConstants.MELEE_ACTIVE_SECONDS;
    private double left;
    private double top;

    public MeleeAttack(UUID id, UUID sourceId, int direction, List<AttackEffect> effects) {
        super(id, sourceId, direction, effects);
    }

    @Override
    boolean update(Map<UUID, GameCharacter> characters, Collection<NPC> npcs, Arena arena, double seconds) {
        GameCharacter source = characters.get(sourceId());
        if (source == null) {
            return true;
        }
        left = direction() > 0 ? source.x() + GameConstants.PLAYER_WIDTH : source.x() - GameConstants.MELEE_RANGE;
        top = source.y();
        for (GameCharacter character : characters.values()) {
            UUID targetId = character.playerId();
            if (!targetId.equals(sourceId()) && !alreadyHit.contains(targetId)
                    && character.overlapsRect(left, top, GameConstants.MELEE_RANGE, GameConstants.PLAYER_HEIGHT)) {
                alreadyHit.add(targetId);
                hit(character);
            }
        }
        for (NPC npc : npcs) {
            if (npc.alive() && !alreadyHitNpcs.contains(npc.id())
                    && npc.overlapsRect(left, top, GameConstants.MELEE_RANGE, GameConstants.PLAYER_HEIGHT)) {
                alreadyHitNpcs.add(npc.id());
                hit(npc);
            }
        }
        remainingSeconds -= seconds;
        return remainingSeconds <= 0;
    }

    @Override
    AttackState state() {
        return new AttackState(id(), sourceId(), AttackType.MELEE, direction(), left, top,
                GameConstants.MELEE_RANGE, GameConstants.PLAYER_HEIGHT);
    }
}