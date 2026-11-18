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
    private static ExaminatorSnapshot state(UUID owner, String phase, double time, long sequence,
            double dx, double dy) {
        return new ExaminatorSnapshot(owner, 140, 140, phase, 30 - time,
                switch (phase) { case "FLY" -> 0.8; case "INSPECT" -> 0.6;
                    case "AIM" -> 0.5; case "IDLE" -> 0; default -> 0.2; },
                time, sequence, 147, 137, 147 + dx, 137 + dy,
                phase.equals("FIRE") ? List.of(new ExaminatorShotSnapshot(sequence, 147, 137,
                        147 + dx, 137 + dy, 0.2)) : List.of());
    }

    @Test
    void authoritativeShotsDeduplicateExpireIgnoreStaleTimeAndClearOnCancellationOrDisconnect() {
        AtomicLong time = new AtomicLong(1_000_000_000);
        ParticleSystem particles = new ParticleSystem(new java.util.Random(1), 100);
        ExaminatorPresentation presentation = new ExaminatorPresentation(particles, time::get);
        UUID owner = UUID.randomUUID();
        ExaminatorSnapshot fire = state(owner, "FIRE", 2.1, 1, 100, 100);
        presentation.update(List.of(fire));
        assertEquals(1, presentation.beamCount());
        assertEquals(12, particles.size());
        time.addAndGet(150_000_000);
        presentation.update(List.of(fire));
        presentation.update(List.of(state(owner, "FIRE", 2.15, 1, 100, 100)));
        assertEquals(12, particles.size(), "Newer snapshots of the same shot cannot duplicate bursts");
        time.addAndGet(51_000_000);
        assertFalse(presentation.isActive(), "Repeated feedback cannot extend a shot");
        presentation.update(List.of(fire));
        assertEquals(0, presentation.beamCount());
        presentation.update(List.of(state(owner, "AIM", 2.8, 1, -100, -100)));
        assertTrue(presentation.isActive());
        presentation.update(List.of(fire)); // Older FIRE must not replay or replace newer AIM.
        assertEquals(0, presentation.beamCount());
        presentation.update(List.of(state(owner, "FIRE", 3, 2, -100, -100)));
        assertEquals(1, presentation.beamCount());
        presentation.update(List.of(state(owner, "IDLE", 3.01, 2, 0, 0)));
        assertFalse(presentation.isActive(), "Cancellation clears beams");
        presentation.update(List.of());
        assertFalse(presentation.isActive());
        presentation.clear();
        presentation.update(List.of(fire));
        assertEquals(1, presentation.beamCount(), "New sessions can restart identities");
        presentation.clear();
        assertFalse(presentation.isActive());
    }

    @Test
    void thrustersAreTimeBasedStopAtPhaseEndAndPaintNeverEmits() {
        AtomicLong time = new AtomicLong(1_000_000_000);
        ParticleSystem particles = new ParticleSystem(new java.util.Random(1), 100);
        ExaminatorPresentation presentation = new ExaminatorPresentation(particles, time::get);
        UUID owner = UUID.randomUUID();
        presentation.update(List.of(state(owner, "FLY", 0, 0, 0, 0)));
        for (int i = 0; i < 10; i++) { presentation.advance(0.01); }
        assertEquals(1, particles.size());
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            for (int i = 0; i < 10; i++) { presentation.draw(g, AssetManager.getInstance()); }
        } finally { g.dispose(); }
        assertEquals(1, particles.size());
        time.addAndGet(900_000_000);
        presentation.advance(0.1);
        assertEquals(1, particles.size(), "No continuous effects after the authoritative phase expires");
        presentation.update(List.of(state(owner, "INSPECT", 0.9, 0, 0, 0)));
        presentation.advance(0.1);
        assertEquals(1, particles.size());
        presentation.clear();
        particles.update(1);
        assertFalse(particles.isActive());
    }

    @Test
    void classpathArtHasNormalizedTransparentCanvasesAndEveryMissingAssetHasFallback() throws Exception {
        AssetManager assets = AssetManager.getInstance();
        BufferedImage canvas = new BufferedImage(1000, 680, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(new Color(38, 48, 68)); g.fillRect(0, 0, 1000, 680);
        try {
            for (int i = 0; i < ExaminatorArt.KEYS.length; i++) {
                String key = ExaminatorArt.KEYS[i];
                BufferedImage loaded = (BufferedImage) assets.getImage(key);
                assertNotNull(loaded, key);
                if (!key.contains("LASER")) {
                    assertEquals(128, loaded.getWidth(), key);
                    assertEquals(128, loaded.getHeight(), key);
                    assertEquals(0, loaded.getRGB(0, 0) >>> 24, key);
                }
                int x = (i % 6) * 160;
                int y = (i / 6) * 160;
                ExaminatorArt.draw(g, assets, key, new Rectangle2D.Double(x + 12, y + 24, 112, 112));
                g.setColor(Color.WHITE);
                g.drawString(key.replace("EXAMINATOR_", ""), x + 4, y + 16);
                BufferedImage fallback = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                Graphics2D fg = fallback.createGraphics();
                try { ExaminatorArt.fallback(fg, key, new Rectangle2D.Double(0, 0, 64, 64)); }
                finally { fg.dispose(); }
                assertTrue(hasPixels(fallback, 0, 0, 64, 64), key);
            }
        } finally { g.dispose(); }
        Files.createDirectories(Path.of("target"));
        ImageIO.write(canvas, "png", Path.of("target/examinator-poses.png").toFile());
    }

    @Test
    void eightDirectionsAimLaserAndParticlesRenderOffscreenIncludingZeroLengthShot() throws Exception {
        BufferedImage canvas = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(new Color(38, 48, 68)); g.fillRect(0, 0, 1200, 600);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double dx = Math.cos(angle) * 110;
                double dy = Math.sin(angle) * 110;
                assertEquals(ExaminatorArt.DIRECTIONS[i], ExaminatorArt.direction(dx, dy));
                Graphics2D cell = (Graphics2D) g.create();
                cell.translate((i % 4) * 300, (i / 4) * 300);
                AtomicLong time = new AtomicLong(1_000_000_000);
                ParticleSystem particles = new ParticleSystem(new java.util.Random(1), 100);
                ExaminatorPresentation presentation = new ExaminatorPresentation(particles, time::get);
                UUID owner = UUID.randomUUID();
                presentation.update(List.of(state(owner, "AIM", 1.6, 0, dx, dy)));
                presentation.draw(cell, AssetManager.getInstance());
                presentation.update(List.of(state(owner, "FIRE", 2.1, 1, dx, dy)));
                presentation.draw(cell, AssetManager.getInstance());
                particles.update(0.07); particles.draw(cell);
                cell.setColor(Color.WHITE); cell.drawString(ExaminatorArt.DIRECTIONS[i], 10, 20);
                cell.dispose();
            }
            ExaminatorArt.laser(g, AssetManager.getInstance(), 0, 0, 0, 0);
            ExaminatorArt.laser(g, AssetManager.getInstance(), 10, 590, 10.01, 590);
        } finally { g.dispose(); }
        ImageIO.write(canvas, "png", Path.of("target/examinator-directions.png").toFile());
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
    void panelRendersAuthoritativePhasesAndStopsPresentationOnClear() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel();
            panel.setSize(960, 540);
            UUID owner = UUID.randomUUID();
            PlayerSnapshot player = new PlayerSnapshot(owner, 0, "Owner", 100, 384, 42, 64, true,
                    false, 0, 100, 100, 0, "PATASIJUS", false, true);
            int tick = 0;
            for (String phase : List.of("FLY", "INSPECT", "ACQUIRE", "AIM", "FIRE", "IDLE")) {
                tick++;
                ExaminatorSnapshot ability = state(owner, phase, tick, phase.equals("FIRE") ? 1 : 0, 350, 200);
                panel.setSnapshot(new WorldSnapshot(tick, new ArenaSnapshot("training", 960, 540, List.of()),
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
