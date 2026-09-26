package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;

public final class SpeedBoostItem extends Item {
    public SpeedBoostItem(UUID id, Position position) {
        super(id, position, GameConstants.ITEM_DURATION_SECONDS);
    }

    @Override
    public ItemType type() { return ItemType.SPEED_BOOST; }

    @Override
    public void apply(GameCharacter target) {
        target.applySpeedMultiplier(GameConstants.SPEED_BOOST_MULTIPLIER, durationSeconds());
    }
}