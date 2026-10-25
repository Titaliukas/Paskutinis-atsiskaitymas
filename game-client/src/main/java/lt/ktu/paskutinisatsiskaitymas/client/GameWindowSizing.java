package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Dimension;
import java.awt.Rectangle;

/** EDT-owned first-join sizing policy; all dimensions are logical Swing units. */
final class GameWindowSizing {
    private boolean initialized;

    boolean onStatus(ConnectionStatus.State state) {
        if (initialized || state != ConnectionStatus.State.CONNECTED) {
            return false;
        }
        initialized = true;
        return true;
    }

    /** Fits a viewport up to 960x540 plus measured chrome, retaining the location where possible. */
    static Rectangle bounds(Rectangle current, Rectangle workArea, Dimension overhead) {
        if (overhead.width < 0 || overhead.height < 0) {
            throw new IllegalArgumentException("Window overhead must be nonnegative");
        }
        // Integer multiples of 16x9 keep an exact aspect ratio after Swing layout rounding.
        int units = Math.min(60, Math.min((workArea.width - overhead.width) / 16,
                (workArea.height - overhead.height) / 9));
        if (units < 1) {
            throw new IllegalArgumentException("Work area cannot fit window chrome and a 16x9 viewport");
        }
        int width = units * 16 + overhead.width;
        int height = units * 9 + overhead.height;
        int x = Math.max(workArea.x, Math.min(current.x, workArea.x + workArea.width - width));
        int y = Math.max(workArea.y, Math.min(current.y, workArea.y + workArea.height - height));
        return new Rectangle(x, y, width, height);
    }
}
