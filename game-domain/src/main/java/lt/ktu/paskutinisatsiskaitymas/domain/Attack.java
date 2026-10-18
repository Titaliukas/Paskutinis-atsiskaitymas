package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bridge abstraction: HOW an attack is delivered (hitbox, movement, lifetime).
 * What it does on contact is delegated to the {@link AttackEffect} list (the bridge), so delivery
 * kinds and effects vary independently. All methods are game-loop-thread confined.
 */
public abstract sealed class Attack permits MeleeAttack, ProjectileAttack {
    private final UUID id;
    private final UUID sourceId;
    private final int direction;
    private final List<AttackEffect> effects;

    protected Attack(UUID id, UUID sourceId, int direction, List<AttackEffect> effects) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("Direction must be -1 or 1");
        }
        this.direction = direction;
        this.effects = List.copyOf(effects);
        if (this.effects.isEmpty()) {
            throw new IllegalArgumentException("An attack needs at least one effect");
        }
    }

    public UUID id() { return id; }
    public UUID sourceId() { return sourceId; }
    public int direction() { return direction; }

    /** Advances the attack by one tick; returns true when it is finished and must be removed. */
    abstract boolean update(Map<UUID, GameCharacter> characters, Collection<NPC> npcs,
            Arena arena, double seconds);

    abstract AttackState state();

    /** Single place where shields block attacks, then every effect is delegated across the bridge. */
    protected final void hit(GameCharacter target) {
        if (target.shielded()) {
            return;
        }
        for (AttackEffect effect : effects) {
            effect.applyTo(target, direction);
        }
    }

    /** NPCs have no shield; a dead NPC cannot be hit again. */
    protected final void hit(NPC target) {
        if (!target.alive()) {
            return;
        }
        for (AttackEffect effect : effects) {
            effect.applyTo(target, direction);
        }
    }
}