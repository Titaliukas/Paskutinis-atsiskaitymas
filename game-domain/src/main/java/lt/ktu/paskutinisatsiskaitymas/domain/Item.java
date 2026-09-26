package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import java.util.Objects;

/** A world item declaration; pickup, effects and inventory rules are deferred. */
public sealed abstract class Item permits SpeedBoostItem, JumpBoostItem, ShieldItem {
    private final UUID id;
    private final Position position;
    private final double durationSeconds;
    private boolean collected;
    
    protected Item(UUID id, Position position, double durationSeconds) {
        this.id = Objects.requireNonNull(id, "id");
        this.position = Objects.requireNonNull(position, "position");
        this.durationSeconds = durationSeconds;
    }

    public UUID id() { return id; }
    public Position position() { return position; }
    public double durationSeconds() { return durationSeconds; }
    public boolean collected() { return collected; }
    public void markCollected() { collected = true; }

    public abstract ItemType type();
    public abstract void apply(GameCharacter target);
}
