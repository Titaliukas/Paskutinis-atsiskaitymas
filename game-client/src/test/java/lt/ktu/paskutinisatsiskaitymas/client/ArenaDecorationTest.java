package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaDecorationTest {
    private ArenaSnapshot arena() {
        return new ArenaSnapshot("combat", 960, 540, List.of(new PlatformSnapshot(0, 421, 960, 119),
                new PlatformSnapshot(450, 280, 120, 6, "service")), new SidePassageSnapshot(343, 421));
    }

    @Test
    void bothOpeningInteriorsRemainVisibleAboveGroundWithAllFourHudCardsAtBothSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<PlayerSnapshot> players = new ArrayList<>();
            for (int slot = 0; slot < 4; slot++) {
                players.add(player(slot, 100 + slot * 200, 357, true));
            }
            for (int[] size : new int[][]{{960, 540}, {640, 360}, {640, 400}}) {
                GamePanel panel = new GamePanel();
                panel.setSize(size[0], size[1]);
                panel.setSnapshot(new WorldSnapshot(0, arena(), players, List.of(), List.of(), List.of(), List.of()));
                BufferedImage image = GamePanelArenaTest.render(panel);
                double scale = size[0] / 960.0;
                double oy = (size[1] - 540 * scale) / 2;
                for (double x : new double[]{20, 940}) {
                    assertEquals(new Color(34, 43, 51).getRGB(), image.getRGB((int) (x * scale),
                            (int) (oy + 413 * scale)), "HUD must not cover the lower opening interior");
                }
                assertEquals(new Color(88, 101, 109).getRGB(), image.getRGB((int) (500 * scale),
                        (int) (oy + 284 * scale)), "Service platform has visible physical volume");
                panel.clearGame();
            }
        });
    }

    @Test
    void trainingBottomCardsLeaveTheStandingPlayersFeetVisible() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var training = new ArenaSnapshot("training", 960, 540,
                    List.of(new PlatformSnapshot(0, 448, 960, 92)));
            List<PlayerSnapshot> players = new ArrayList<>();
            for (int slot = 0; slot < 4; slot++) players.add(player(slot, 180 + slot * 180, 384, true));
            for (int width : new int[]{960, 640}) {
                GamePanel panel = new GamePanel();
                panel.setSize(width, width * 9 / 16);
                panel.setSnapshot(new WorldSnapshot(0, training, List.of(players.getFirst()),
                        List.of(), List.of(), List.of(), List.of()));
                BufferedImage single = GamePanelArenaTest.render(panel);
                panel.setSnapshot(new WorldSnapshot(0, training, players, List.of(), List.of(), List.of(), List.of()));
                BufferedImage four = GamePanelArenaTest.render(panel);
                double scale = width / 960.0;
                assertEquals(single.getRGB((int) (201 * scale), (int) (449 * scale)),
                        four.getRGB((int) (201 * scale), (int) (449 * scale)),
                        "Bottom HUD cannot cover the first player's contact with the track");
                panel.clearGame();
            }
        });
    }

    @Test
    void contactShadowRequiresGroundedStateAndAnActualSupportingSurface() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel();
            panel.setSize(960, 540);
            var standing = player(0, 300, 357, true);
            panel.setSnapshot(world(standing));
            BufferedImage grounded = GamePanelArenaTest.render(panel);
            panel.setSnapshot(world(player(0, 300, 357, false)));
            BufferedImage airborne = GamePanelArenaTest.render(panel);
            assertNotEquals(grounded.getRGB(321, 422), airborne.getRGB(321, 422));
            assertTrue(ArenaGeometryRenderer.supported(arena(), 300, 421, 42));
            assertFalse(ArenaGeometryRenderer.supported(arena(), 300, 400, 42));
            panel.clearGame();
        });
    }

    @Test
    void itemArtFitsTwentyTwoWorldUnitsWithoutChangingItsSnapshot() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = new GamePanel();
            panel.setSize(960, 540);
            panel.setSnapshot(new WorldSnapshot(0, arena(), List.of(), List.of(), List.of(), List.of(), List.of()));
            BufferedImage base = GamePanelArenaTest.render(panel);
            ItemSnapshot item = new ItemSnapshot(UUID.randomUUID(), "SHIELD", 480, 150);
            panel.setSnapshot(new WorldSnapshot(0, arena(), List.of(), List.of(item), List.of(), List.of(), List.of()));
            BufferedImage icon = GamePanelArenaTest.render(panel);
            int changed = 0;
            for (int x = 460; x <= 500; x++) {
                for (int y = 130; y <= 170; y++) {
                    if (base.getRGB(x, y) != icon.getRGB(x, y)) {
                        changed++;
                        assertTrue(x >= 469 && x < 491 && y >= 139 && y < 161);
                    }
                }
            }
            assertTrue(changed > 0);
            assertEquals(480, item.x());
            assertEquals(150, item.y());
            panel.clearGame();
        });
    }

    private WorldSnapshot world(PlayerSnapshot player) {
        return new WorldSnapshot(0, arena(), List.of(player), List.of(), List.of(), List.of(), List.of());
    }

    private PlayerSnapshot player(int slot, double x, double y, boolean grounded) {
        return new PlayerSnapshot(new UUID(0, slot + 1), slot, "Player " + slot,
                x, y, 42, 64, grounded, false, 0, 100, 100, 0, "PATASIJUS", false);
    }
}
