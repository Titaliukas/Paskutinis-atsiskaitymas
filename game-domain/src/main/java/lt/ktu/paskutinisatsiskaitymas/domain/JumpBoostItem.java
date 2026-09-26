package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;

public final class JumpBoostItem extends Item {
    public JumpBoostItem(UUID id, Position position) {
        super(id, position, GameConstants.ITEM_DURATION_SECONDS);
    }

    @Override
    public ItemType type() { return ItemType.JUMP_BOOST; }

    @Override
    public void apply(GameCharacter target) {
        target.applyJumpMultiplier(GameConstants.JUMP_BOOST_MULTIPLIER, durationSeconds());
    }
}