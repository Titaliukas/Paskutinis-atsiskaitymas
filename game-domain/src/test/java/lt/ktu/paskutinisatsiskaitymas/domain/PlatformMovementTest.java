package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformMovementTest {
    @Test
    void stationaryUpdateRetainsPositionAndBounds() {
        Platform platform = new StationaryPlatform(UUID.randomUUID(), new Position(20, 30), 120, 10);
        platform.update(100);
        assertEquals(new Position(20, 30), platform.position());
        assertEquals(platform.position(), platform.previousPosition());
        assertEquals(140, platform.right());
        assertEquals(40, platform.bottom());
    }

    @Test
    void bothAxesReverseAtEndpointsAndPreserveOvershootInEitherInitialDirection() {
        for (boolean horizontal : new boolean[]{true, false}) {
            for (int sign : new int[]{1, -1}) {
                Platform platform = moving(horizontal, 200 + sign * 100, 50);
                platform.update(2);
                assertCoordinate(platform, horizontal, 200 + sign * 100);
                platform.update(0.5);
                assertCoordinate(platform, horizontal, 200 + sign * 75);
                platform.update(4 * 1000 + 2);
                assertCoordinate(platform, horizontal, 200 + sign * 25);
                assertEquals(platform.position().x() + 120, platform.right(), 1e-6);
                assertEquals(platform.position().y() + 10, platform.bottom(), 1e-6);
            }
        }
    }

    @Test
    void timePartitionDoesNotChangeMotion() {
        for (boolean horizontal : new boolean[]{true, false}) {
            Platform whole = moving(horizontal, 300, 50);
            Platform split = whole.clone();
            whole.update(17.25);
            for (int i = 0; i < 1035; i++) split.update(1.0 / 60);
            assertEquals(whole.left(), split.left(), 1e-6);
            assertEquals(whole.top(), split.top(), 1e-6);
        }
    }

    @Test
    void updateRefreshesGeometryBeforeCarryingPassengers() {
        Platform platform = moving(true, 300, 50);
        PlatformPassenger observer = new PlatformPassenger() {
            public double platformX() { return 210; }
            public double platformFeetY() { return 200; }
            public double platformWidth() { return 42; }
            public boolean canRidePlatform() { return true; }
            public void moveWithPlatform(double dx, double dy) {
                assertEquals(new Position(200, 200), platform.previousPosition());
                assertEquals(new Position(225, 200), platform.position());
                assertEquals(225, platform.left());
                assertEquals(345, platform.right());
                assertEquals(25, dx);
                assertEquals(0, dy);
            }
        };
        platform.update(0.5, List.of(observer));
    }

    @Test
    void clonesAndRelocatedCopiesKeepSubtypePhaseAndDirectionWithoutSharingMotion() {
        for (boolean horizontal : new boolean[]{true, false}) {
            Platform original = moving(horizontal, 300, 50);
            original.update(2.5); // Returning from the far endpoint.
            Platform clone = original.clone();
            Position relocated = new Position(original.left() + 40, original.top() + 30);
            Platform shifted = original.copyAt(relocated);
            assertEquals(original.getClass(), clone.getClass());
            assertEquals(original.getClass(), shifted.getClass());
            assertNotEquals(original.id(), clone.id());
            assertNotEquals(original.id(), shifted.id());
            assertEquals(Platform.Kind.SERVICE, shifted.kind());
            original.update(0.25);
            assertEquals(relocated, shifted.position());
            clone.update(0.25);
            shifted.update(0.25);
            assertEquals(original.position(), clone.position());
            assertEquals(original.left() + 40, shifted.left(), 1e-6);
            assertEquals(original.top() + 30, shifted.top(), 1e-6);
            original.update(2);
            shifted.update(2);
            assertEquals(original.left() + 40, shifted.left(), 1e-6);
            assertEquals(original.top() + 30, shifted.top(), 1e-6);
        }
    }

    @Test
    void rejectsInvalidDurationsSpeedsEndpointsDimensionsAndCompletePaths() {
        Platform platform = moving(true, 300, 50);
        for (double bad : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> platform.update(bad));
            assertThrows(IllegalArgumentException.class, () -> moving(true, 300, bad));
            assertThrows(IllegalArgumentException.class, () -> moving(false, 300, bad));
            assertThrows(IllegalArgumentException.class, () -> new StationaryPlatform(
                    UUID.randomUUID(), new Position(0, 0), bad, 10));
        }
        for (boolean horizontal : new boolean[]{true, false}) {
            assertThrows(IllegalArgumentException.class, () -> moving(horizontal, 200, 50));
            assertThrows(IllegalArgumentException.class, () -> moving(horizontal, Double.NaN, 50));
            assertThrows(IllegalArgumentException.class, () -> moving(horizontal, Double.POSITIVE_INFINITY, 50));
            Platform outside = moving(horizontal, 1000, 50);
            assertThrows(IllegalArgumentException.class,
                    () -> TestArenas.geometry("Outside path", 960, 540, List.of(outside)));
        }
        assertEquals(new Position(200, 200), platform.position());
    }

    private Platform moving(boolean horizontal, double end, double speed) {
        return horizontal
                ? new HorizontalPlatform(UUID.randomUUID(), new Position(200, 200), 120, 10,
                        Platform.Kind.SERVICE, end, speed)
                : new VerticalPlatform(UUID.randomUUID(), new Position(200, 200), 120, 10,
                        Platform.Kind.SERVICE, end, speed);
    }

    private void assertCoordinate(Platform platform, boolean horizontal, double expected) {
        assertEquals(expected, horizontal ? platform.left() : platform.top(), 1e-6);
        assertEquals(200, horizontal ? platform.top() : platform.left(), 1e-6);
    }
}
