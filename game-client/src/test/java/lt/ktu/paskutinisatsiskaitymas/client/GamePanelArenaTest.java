package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.ArenaSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlatformSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GamePanelArenaTest {
    @Test
    void selectsCachedBackgroundByWireIdentityAndKeepsOriginalDimensions() {
        AssetManager assets = AssetManager.getInstance();
        for (String type : List.of("combat", "training")) {
            var background = assets.arenaBackground(type);
            assertNotNull(background);
            assertSame(background, assets.arenaBackground(type));
            assertEquals(1672, background.getWidth(null));
            assertEquals(941, background.getHeight(null));
        }
        assertNotSame(assets.arenaBackground("combat"), assets.arenaBackground("training"));
        assertNull(assets.arenaBackground("missing"));
    }

    @Test
    void rendersCorrectImageWithoutOpaquePlatformsAndAlignsDebugUnderResizeAndLetterboxing() throws Exception {
        String previous = System.getProperty("game.debug.geometry");
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (String type : List.of("combat", "training")) {
                    var source = (BufferedImage) AssetManager.getInstance().arenaBackground(type);
                    double groundY = type.equals("combat") ? 467 : 480;
                    var world = new WorldSnapshot(0, new ArenaSnapshot(type, 960, 540,
                            List.of(new PlatformSnapshot(0, groundY, 960, 540 - groundY))),
                            List.of(), List.of(), List.of(), List.of(), List.of());
                    for (int[] size : new int[][]{{960, 540}, {640, 360}, {640, 400}, {1000, 540}}) {
                        double scale = Math.min(size[0] / 960.0, size[1] / 540.0);
                        double ox = (size[0] - 960 * scale) / 2;
                        double oy = (size[1] - 540 * scale) / 2;
                        System.clearProperty("game.debug.geometry");
                        GamePanel panel = new GamePanel();
                        panel.setSize(size[0], size[1]);
                        panel.setSnapshot(world);
                        BufferedImage normal = render(panel);
                        // Sample source and destination at the same world coordinate, far from any HUD.
                        int px = (int) (ox + 480 * scale);
                        int py = (int) (oy + 500 * scale);
                        int sx = (int) (((px + 0.5 - ox) / (960 * scale)) * source.getWidth());
                        int sy = (int) (((py + 0.5 - oy) / (540 * scale)) * source.getHeight());
                        assertEquals(source.getRGB(sx, sy), normal.getRGB(px, py), type);
                        if (ox > 0 || oy > 0) assertEquals(Color.BLACK.getRGB(), normal.getRGB(0, 0));
                        System.setProperty("game.debug.geometry", "true");
                        GamePanel debug = new GamePanel();
                        debug.setSize(size[0], size[1]);
                        debug.setSnapshot(world);
                        BufferedImage outline = render(debug);
                        int lineY = (int) Math.round(oy + groundY * scale);
                        assertEquals(Color.MAGENTA.getRGB(), outline.getRGB(px, lineY));
                        assertNotEquals(normal.getRGB(px, lineY), outline.getRGB(px, lineY));
                        assertEquals(normal.getRGB(px, py), outline.getRGB(px, py), "Debug cannot fill the ground");
                        panel.clearGame(); debug.clearGame();
                    }
                }
            });
        } finally {
            if (previous == null) System.clearProperty("game.debug.geometry");
            else System.setProperty("game.debug.geometry", previous);
        }
    }

    static BufferedImage render(GamePanel panel) {
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try { panel.paint(g); } finally { g.dispose(); }
        return image;
    }
}
