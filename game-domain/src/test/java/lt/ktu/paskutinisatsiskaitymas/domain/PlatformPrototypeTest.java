package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformPrototypeTest {
    @Test
    void copiesPreserveServiceRoleWhileKeepingIdentityAndPlacementIndependent() {
        Platform template = new Platform(UUID.randomUUID(), new Position(0, 0), 120, 6, Platform.Kind.SERVICE);
        Platform copy = template.copyAt(new Position(450, 280));
        assertEquals(Platform.Kind.SERVICE, copy.kind());
        assertNotEquals(template.id(), copy.id());
        assertEquals(new Position(0, 0), template.position());
        assertEquals(new Position(450, 280), copy.position());
    }

    @Test
    void cloneCreatesIndependentPlatformAtSamePosition() {
        Platform original = new Platform(UUID.randomUUID(), new Position(10, 20), 100, 30);

        Prototype<Platform> prototype = original;
        Platform copy = prototype.clone();

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

    @Test
    void copiesManyPlatformsFromOneTemplate() {
        Platform template = new Platform(UUID.randomUUID(), new Position(0, 0), 160, 20);
        Position[] positions = {new Position(140, 380), new Position(660, 380),
                new Position(400, 280)};

        Platform[] copies = java.util.Arrays.stream(positions).map(template::copyAt).toArray(Platform[]::new);

        assertEquals(new Position(0, 0), template.position());
        assertEquals(3, copies.length);
        for (int index = 0; index < copies.length; index++) {
            assertNotSame(template, copies[index]);
            assertNotEquals(template.id(), copies[index].id());
            assertEquals(positions[index], copies[index].position());
            assertEquals(template.width(), copies[index].width());
            assertEquals(template.height(), copies[index].height());
        }
        assertEquals(3, java.util.Arrays.stream(copies).map(Platform::id).distinct().count());
    }
}
