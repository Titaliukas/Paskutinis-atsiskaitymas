package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformPrototypeTest {
    @Test
    void cloneCreatesIndependentPlatformAtSamePosition() {
        Platform original = new Platform(UUID.randomUUID(), new Position(10, 20), 100, 30);

        Platform copy = original.clone();

        assertNotSame(original, copy);
        assertNotEquals(original.id(), copy.id());
        assertEquals(original.position(), copy.position());
        assertEquals(original.width(), copy.width());
        assertEquals(original.height(), copy.height());
    }

    @Test
    void copyAtMovesOnlyTheClone() {
        Position originalPosition = new Position(10, 20);
        Position newPosition = new Position(40, 50);
        Platform original = new Platform(UUID.randomUUID(), originalPosition, 100, 30);

        Platform copy = original.copyAt(newPosition);

        assertNotSame(original, copy);
        assertNotEquals(original.id(), copy.id());
        assertEquals(originalPosition, original.position());
        assertEquals(newPosition, copy.position());
        assertEquals(original.width(), copy.width());
        assertEquals(original.height(), copy.height());
        assertThrows(NullPointerException.class, () -> original.copyAt(null));
    }
}
