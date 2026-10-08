package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.ArenaSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.NPCSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlatformSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NPCPresentationTest {
    private static NPCSnapshot npc(String activity, long sequence) {
        return new NPCSnapshot(1, 300, 416, 42, 64, activity, true, sequence);
    }

    @Test
    void everyActivitySelectsItsResourcePreservesAspectAndAlignsGroundContact() {
        NPCPresentation presentation = new NPCPresentation(() -> 0);
        double standingWidth = 0;
        for (String activity : List.of("PATROL", "CHASE", "ATTACK", "FLEE")) {
            NPCSnapshot npc = npc(activity, 0);
            var style = presentation.style(npc);
            assertEquals("NPC_" + activity, style.assetKey());
            var image = (BufferedImage) AssetManager.getInstance().getImage(style.assetKey());
            assertNotNull(image);
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(0, image.getRGB(0, 0) >>> 24, "Transparent corner");
            var bounds = style.bounds(npc, image, 2, 20, 10);
            assertEquals((double) image.getWidth() / image.getHeight(),
                    bounds.getWidth() / bounds.getHeight(), 1e-9);
            assertEquals(10 + 480 * 2, bounds.getY() + bounds.getHeight() * style.contactFraction(), 1e-9);
            assertEquals(42, npc.width(), "Pose must not change authoritative hitbox");
            assertEquals(64, npc.height());
            if (activity.equals("PATROL")) {
                standingWidth = bounds.getWidth();
            } else if (activity.equals("FLEE")) {
                assertTrue(bounds.getWidth() > standingWidth);
                assertEquals(64, bounds.getHeight(), "Crawling pose is half standing height at 2x scale");
            }
        }
    }

    @Test
    void initializesSequenceThenLatchesAnAttackEvenWhenServerAlreadyFlees() {
        AtomicLong now = new AtomicLong(1_000_000_000);
        NPCPresentation presentation = new NPCPresentation(now::get);
        NPCSnapshot initial = npc("FLEE", 7);
        presentation.update(List.of(initial));
        assertEquals("NPC_FLEE", presentation.style(initial).assetKey());
        assertFalse(presentation.hasActiveLatch());
        NPCSnapshot hit = npc("FLEE", 8);
        presentation.update(List.of(hit));
        assertEquals("FLEE", hit.activity(), "Label remains authoritative");
        assertEquals("NPC_ATTACK", presentation.style(hit).assetKey());
        assertTrue(presentation.hasActiveLatch());
        now.addAndGet(100_000_000);
        presentation.update(List.of(hit));
        now.addAndGet(79_999_999);
        assertEquals("NPC_ATTACK", presentation.style(hit).assetKey(), "Same sequence must not extend latch");
        now.incrementAndGet();
        assertEquals("NPC_FLEE", presentation.style(hit).assetKey());
        assertFalse(presentation.hasActiveLatch());
        presentation.update(List.of(npc("FLEE", 10)));
        assertTrue(presentation.hasActiveLatch(), "Skipped sequence values still signal an attempt");
        presentation.update(List.of());
        assertFalse(presentation.hasActiveLatch());
        presentation.update(List.of(npc("FLEE", 12)));
        assertFalse(presentation.hasActiveLatch(), "A reappearing NPC starts without replay");
        presentation.clear();
        assertFalse(presentation.hasActiveLatch());
    }

    @Test
    void rendersPoseSmokeSheetOnEDTWithoutChangingSnapshotGeometry() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel();
            panel.setSize(960, 540);
            List<NPCSnapshot> npcs = List.of(
                    new NPCSnapshot(1, 130, 416, 42, 64, "PATROL", true, 0),
                    new NPCSnapshot(2, 300, 416, 42, 64, "CHASE", true, 0),
                    new NPCSnapshot(3, 470, 416, 42, 64, "ATTACK", true, 0),
                    new NPCSnapshot(4, 640, 416, 42, 64, "FLEE", true, 0),
                    new NPCSnapshot(5, 810, 416, 42, 64, "FLEE", false, 0));
            WorldSnapshot snapshot = new WorldSnapshot(0,
                    new ArenaSnapshot(960, 540, List.of(new PlatformSnapshot(0, 480, 960, 60))),
                    List.of(), List.of(), npcs);
            panel.setSnapshot(snapshot);
            BufferedImage image = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                panel.paint(graphics);
                Files.createDirectories(Path.of("target"));
                ImageIO.write(image, "png", Path.of("target/npc-pose-smoke.png").toFile());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            } finally {
                graphics.dispose();
                panel.clearGame();
                panel.removeNotify();
            }
            assertEquals(npcs, snapshot.npcs());
            assertEquals(0xff263044, image.getRGB(100, 450), "Transparent backgrounds retain arena sky");
            assertEquals(0xff534537, image.getRGB(650, 510), "Ground remains visible below the poses");
        });
    }
}
