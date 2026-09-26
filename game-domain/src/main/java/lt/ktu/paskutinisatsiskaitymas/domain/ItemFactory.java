package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.Item;
import lt.ktu.paskutinisatsiskaitymas.domain.ItemType;
import lt.ktu.paskutinisatsiskaitymas.domain.JumpBoostItem;
import lt.ktu.paskutinisatsiskaitymas.domain.Position;
import lt.ktu.paskutinisatsiskaitymas.domain.ShieldItem;
import lt.ktu.paskutinisatsiskaitymas.domain.SpeedBoostItem;

public final class ItemFactory {

    public Item create(ItemType type, Position position) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        return switch (type) {
            case SPEED_BOOST -> new SpeedBoostItem(UUID.randomUUID(), position);
            case JUMP_BOOST -> new JumpBoostItem(UUID.randomUUID(), position);
            case SHIELD -> new ShieldItem(UUID.randomUUID(), position);
        };
    }
}