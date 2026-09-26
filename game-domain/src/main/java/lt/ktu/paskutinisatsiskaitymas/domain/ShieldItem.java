package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;

public final class ShieldItem extends Item {
    public ShieldItem(UUID id, Position position) {
        super(id, position, GameConstants.ITEM_DURATION_SECONDS);
    }

    @Override
    public ItemType type() { return ItemType.SHIELD; }

    @Override
    public void apply(GameCharacter target) {
        target.applyShield(durationSeconds());
    }
}