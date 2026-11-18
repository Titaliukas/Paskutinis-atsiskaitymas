package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExaminatorPresentationTest {
    @Test
    void authoritativeVolleyDeduplicatesExpiresAndClearsWithoutRetainingRemovedOwner() {
        AtomicLong time = new AtomicLong(1_000_000_000);
        ExaminatorPresentation presentation = new ExaminatorPresentation(time::get);
        UUID owner = UUID.randomUUID();
        ExaminatorSnapshot fire = new ExaminatorSnapshot(owner, 100, 50, "FIRE", 9.5, 0.25, 1, 100, 50,
                List.of(new ShotEndpoint(700, 400)));
        presentation.update(List.of(fire));
        assertEquals(1, presentation.volleyCount());
        time.addAndGet(200_000_000);
        presentation.update(List.of(fire));
        time.addAndGet(51_000_000);
        assertFalse(presentation.isActive(), "Repeated snapshots cannot extend a volley");
        presentation.update(List.of(fire));
        assertEquals(0, presentation.volleyCount(), "Expired sequence must not replay");
        presentation.update(List.of(new ExaminatorSnapshot(owner, 100, 50, "SCAN", 10, 0.5, 1, 100, 50, List.of())));
        assertTrue(presentation.isActive());
        presentation.update(List.of());
        assertFalse(presentation.isActive());
        presentation.clear();
        presentation.update(List.of(fire));
        assertEquals(1, presentation.volleyCount(), "A new session may start at the same sequence");
        presentation.clear();
        assertFalse(presentation.isActive());
    }

    @Test
    void everyMissingAssetHasAPixelFallbackAndAllFourProneSkinsRenderWithStableFeet() throws Exception {
        BufferedImage canvas = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            int x = 20;
            for (String key : ExaminatorArt.KEYS) {
                ExaminatorArt.fallback(g, key, new Rectangle2D.Double(x, 20, 64, 64));
                assertTrue(hasPixels(canvas, x, 20, 64, 64), key);
                x += 90;
            }
            x = 50;
            for (String character : List.of("PATASIJUS", "VACIJUS", "JURGELINIS", "GOGIS")) {
                Rectangle2D standing = new Rectangle2D.Double(x, 200, 42, 64);
                Rectangle2D prone = CharacterPose.bounds(standing, true);
                assertEquals(standing.getMaxY(), prone.getMaxY());
                assertEquals(64, prone.getWidth());
                assertEquals(42, prone.getHeight());
                CharacterSkin skin = CharacterSkin.from(CharacterFactories.forCharacter(character));
                CharacterPose.draw(g, skin.body(), standing, true, true, Color.PINK);
                assertTrue(hasPixels(canvas, (int) prone.getX(), (int) prone.getY(), 64, 42));
                CharacterPose.draw(g, skin.body(), new Rectangle2D.Double(x, 330, 42, 64), false, true, Color.PINK);
                x += 200;
            }
        } finally { g.dispose(); }
        Files.createDirectories(Path.of("target"));
        ImageIO.write(canvas, "png", Path.of("target/examinator-assets-prone.png").toFile());
    }

    @Test
    void proneRotationKeepsFeetAnchorAndDoesNotScaleTheRenderer() {
        for (boolean facesRight : List.of(true, false)) {
            BufferedImage image = new BufferedImage(150, 150, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                CharacterPose.draw(g, (graphics, bounds, facing) -> {
                    graphics.setColor(Color.WHITE);
                    graphics.fill(bounds);
                }, new Rectangle2D.Double(50, 36, 42, 64), facesRight, true, Color.PINK);
            } finally { g.dispose(); }
            assertNotEquals(0, image.getRGB(71, 79) >>> 24);
            for (int y = 100; y < 150; y++) {
                for (int x = 0; x < 150; x++) {
                    assertEquals(0, image.getRGB(x, y) >>> 24, "Feet must stay at y=100");
                }
            }
        }
    }

    @Test
    void panelRendersAuthoritativeScanAndFireAndStopsPresentationOnClear() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel();
            panel.setSize(960, 540);
            UUID owner = UUID.randomUUID();
            PlayerSnapshot player = new PlayerSnapshot(owner, 0, "Owner", 100, 384, 42, 64, true,
                    false, 0, 100, 100, 0, "PATASIJUS", false, true);
            for (String phase : List.of("SCAN", "FIRE", "IDLE")) {
                double remaining = phase.equals("SCAN") ? 0.5 : phase.equals("FIRE") ? 0.25 : 0;
                ExaminatorSnapshot ability = new ExaminatorSnapshot(owner, 121, 356, phase, 9.5, remaining,
                        phase.equals("SCAN") ? 0 : 1, 121, 356,
                        phase.equals("FIRE") ? List.of(new ShotEndpoint(500, 448)) : List.of());
                panel.setSnapshot(new WorldSnapshot(30, new ArenaSnapshot("training", 960, 540, List.of()),
                        List.of(player), List.of(), List.of(), List.of(), List.of(), List.of(ability)));
                BufferedImage image = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                try { panel.paint(g); } finally { g.dispose(); }
                try {
                    ImageIO.write(image, "png", Path.of("target/examinator-" + phase.toLowerCase(java.util.Locale.ROOT)
                            + ".png").toFile());
                } catch (java.io.IOException exception) { throw new AssertionError(exception); }
            }
            panel.clearGame();
            assertFalse(panel.presentationRunning());
        });
    }

    private static boolean hasPixels(BufferedImage image, int x, int y, int width, int height) {
        for (int row = y; row < y + height; row++) {
            for (int col = x; col < x + width; col++) {
                if ((image.getRGB(col, row) >>> 24) != 0) { return true; }
            }
        }
        return false;
    }
}
