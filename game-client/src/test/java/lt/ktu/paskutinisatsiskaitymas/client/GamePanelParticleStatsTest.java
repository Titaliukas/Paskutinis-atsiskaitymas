package lt.ktu.paskutinisatsiskaitymas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.ArenaSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlatformSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import org.junit.jupiter.api.Test;

class GamePanelParticleStatsTest {
    private WorldSnapshot emptyWorld() {
        return new WorldSnapshot(1, new ArenaSnapshot("combat", 960, 540,
                List.of(new PlatformSnapshot(0, 480, 960, 60))),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void clearingAfterASessionPublishesExactlyOneGameplayReport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<ParticleStatsReport> reports = new ArrayList<>();
            GamePanel panel = new GamePanel(reports::add);
            panel.setSize(960, 540);
            panel.setSnapshot(emptyWorld());
            BufferedImage image = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                panel.paint(graphics);
            } finally {
                graphics.dispose();
            }
            panel.clearGame();
            panel.clearGame();
            assertEquals(1, reports.size());
            assertEquals("gameplay", reports.getFirst().value("scenario"));
            assertTrue(Long.parseLong(reports.getFirst().value("frames")) >= 1);
            assertEquals("3000", reports.getFirst().value("particle_cap"));

            panel.setSnapshot(emptyWorld());
            panel.clearGame();
            assertEquals(2, reports.size());
        });
    }

    @Test
    void clearingWithoutASessionPublishesNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<ParticleStatsReport> reports = new ArrayList<>();
            new GamePanel(reports::add).clearGame();
            assertTrue(reports.isEmpty());
        });
    }
}
