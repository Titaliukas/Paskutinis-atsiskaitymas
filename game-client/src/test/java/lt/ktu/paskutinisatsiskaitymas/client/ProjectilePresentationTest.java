package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectilePresentationTest {
    @Test
    void rendersBothVariantsReturnOutlinesAndEffectLabelsConsistentlyOnEDT() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            UUID playerId = UUID.randomUUID();
            var shots = List.of(
                    new ProjectileSnapshot(UUID.randomUUID(), 1, "BOOK", 310, 240, 8, 360, 0, false),
                    new ProjectileSnapshot(UUID.randomUUID(), 1, "DIPLOMA", 430, 240, 8, 360, 0, false),
                    new ProjectileSnapshot(UUID.randomUUID(), 1, "BOOK", 550, 240, 8, -360, 0, true),
                    new ProjectileSnapshot(UUID.randomUUID(), 1, "DIPLOMA", 670, 240, 8, -340, -120, true));
            WorldSnapshot snapshot = new WorldSnapshot(12,
                    new ArenaSnapshot("combat", 960, 540, List.of(new PlatformSnapshot(0, 480, 960, 60))),
                    List.of(new PlayerSnapshot(playerId, 0, "Slowed player", 180, 416, 42, 64,
                            true, false, 247, 100, 100, 0, "PATASIJUS", true)), List.of(),
                    List.of(new NPCSnapshot(1, 550, 416, 42, 64, "CHASE", false, 0, true)), List.of(), shots);
            GamePanel first = new GamePanel();
            GamePanel second = new GamePanel();
            try {
                first.setSnapshot(snapshot);
                second.setSnapshot(snapshot);
                BufferedImage a = render(first, 960, 540);
                BufferedImage b = render(second, 960, 540);
                assertArrayEquals(((DataBufferInt) a.getRaster().getDataBuffer()).getData(),
                        ((DataBufferInt) b.getRaster().getDataBuffer()).getData());
                for (var shot : shots) {
                    assertNotEquals(0xff263044, a.getRGB((int) shot.x(), (int) shot.y()));
                }
                assertTrue(countColor(a, 0xff00ffff, 530, 220, 40, 40) > 20, "Return outline is cyan");
                assertEquals(0, countColor(a, 0xff00ffff, 290, 220, 40, 40));
                assertTrue(countColor(a, 0xffffd264, 180, 480, 100, 20) > 0, "Slow label is visible");
                Files.createDirectories(Path.of("target"));
                ImageIO.write(a, "png", Path.of("target/projectile-effects-smoke.png").toFile());
                ImageIO.write(render(first, 640, 360), "png",
                        Path.of("target/projectile-effects-small.png").toFile());
                assertEquals(shots, snapshot.projectiles(), "Painting leaves authoritative facts unchanged");
                first.clearGame();
                BufferedImage cleared = render(first, 960, 540);
                assertEquals(0, countColor(cleared, 0xff00ffff, 530, 220, 40, 40));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            } finally {
                first.clearGame();
                second.clearGame();
                first.removeNotify();
                second.removeNotify();
            }
        });
    }

    private static BufferedImage render(GamePanel panel, int width, int height) {
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int countColor(BufferedImage image, int color, int x, int y, int width, int height) {
        int count = 0;
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                if (image.getRGB(column, row) == color) {
                    count++;
                }
            }
        }
        return count;
    }
}
