package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** One independent traversal of the live player-slot array, consumed within the owner tick. */
public final class PlayerTargetIterator implements Iterator<GameCharacter> {
    private final GameCharacter[] slots;
    private int index;

    PlayerTargetIterator(GameCharacter[] slots) { this.slots = slots; }

    @Override
    public boolean hasNext() {
        while (index < slots.length && slots[index] == null) {
            index++;
        }
        return index < slots.length;
    }

    @Override
    public GameCharacter next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return slots[index++];
    }
}
