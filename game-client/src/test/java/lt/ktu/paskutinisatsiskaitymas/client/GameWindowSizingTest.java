package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Dimension;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameWindowSizingTest {
    @Test
    void sizesOnlyTheFirstSuccessfulJoinAfterErrorsAndPreservesManualSizeOnReconnect() {
        GameWindowSizing sizing = new GameWindowSizing();
        for (ConnectionStatus.State state : new ConnectionStatus.State[]{ConnectionStatus.State.DISCONNECTED,
                ConnectionStatus.State.CONNECTING, ConnectionStatus.State.ERROR,
                ConnectionStatus.State.DISCONNECTED, ConnectionStatus.State.CONNECTING}) {
            assertFalse(sizing.onStatus(state));
        }
        assertTrue(sizing.onStatus(ConnectionStatus.State.CONNECTED));
        assertFalse(sizing.onStatus(ConnectionStatus.State.CONNECTED), "Repeated status cannot resize");
        assertFalse(sizing.onStatus(ConnectionStatus.State.DISCONNECTED));
        assertFalse(sizing.onStatus(ConnectionStatus.State.CONNECTING));
        assertFalse(sizing.onStatus(ConnectionStatus.State.CONNECTED), "Reconnection retains the user's size");
        assertTrue(new GameWindowSizing().onStatus(ConnectionStatus.State.CONNECTED), "Policy is per window");
    }

    @Test
    void targetsTheViewportRatherThanTheDecoratedFrameAndRetainsLocationWhenItFits() {
        Dimension overhead = new Dimension(32, 193);
        Rectangle current = new Rectangle(29, 59, 1109, 733);
        Rectangle target = GameWindowSizing.bounds(current, new Rectangle(0, 30, 2560, 1343), overhead);
        assertEquals(new Rectangle(29, 59, 992, 733), target);
        assertEquals(960, target.width - overhead.width);
        assertEquals(540, target.height - overhead.height);
        assertEquals(new Rectangle(29, 59, 1109, 733), current, "Input bounds remain unchanged");
    }

    @Test
    void fitsShortNarrowAndOffsetMonitorsWithoutScreenCoordinateOrHiDpiAssumptions() {
        Dimension overhead = new Dimension(32, 193);
        for (Rectangle work : new Rectangle[]{new Rectangle(0, 24, 1280, 656),
                new Rectangle(1920, 40, 800, 700), new Rectangle(-1280, -720, 1280, 680),
                new Rectangle(-900, 60, 640, 500), new Rectangle(0, 30, 1512, 885)}) {
            Rectangle target = GameWindowSizing.bounds(new Rectangle(work.x + work.width - 10,
                    work.y + work.height - 10, 1200, 800), work, overhead);
            assertTrue(work.contains(target), target + " must fit " + work);
            int width = target.width - overhead.width;
            int height = target.height - overhead.height;
            assertEquals(width * 9, height * 16, "Exact 16:9 logical viewport");
            assertTrue(width <= 960 && height <= 540);
            assertTrue(width == 960 || work.width - target.width < 16 || work.height - target.height < 9,
                    "Fit uses the largest available 16x9 multiple without exceeding the target");
        }
    }

    @Test
    void usesMeasuredDecorationAndFormOverheadInsteadOfPlatformSpecificConstants() {
        Rectangle work = new Rectangle(40, 80, 1920, 1000);
        for (Dimension overhead : new Dimension[]{new Dimension(32, 193), new Dimension(48, 240),
                new Dimension(40, 150)}) {
            Rectangle target = GameWindowSizing.bounds(new Rectangle(-10, -10, 1500, 900), work, overhead);
            assertEquals(new Rectangle(40, 80, 960 + overhead.width, 540 + overhead.height), target);
        }
    }
}
