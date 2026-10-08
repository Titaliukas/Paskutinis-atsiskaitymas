package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.ArenaSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.NPCSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlatformSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlayerSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GamePanelHUDTest {
    private final UUID[] ids = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};

    private List<PlayerSnapshot> players(boolean moved, double firstStress) {
        List<PlayerSnapshot> players = new ArrayList<>();
        String[] names = {"Žaidėjas Su Labai Ilgu Vardu 😎", "Bob", "Charlie", "Dora"};
        for (int slot = 0; slot < 4; slot++) {
            players.add(new PlayerSnapshot(ids[slot], slot, names[slot],
                    180 + slot * 180 + (moved ? 15 : 0), moved ? 190 : 210,
                    42, 64, false, slot == 1, moved ? 260 : 0,
                    slot == 0 ? firstStress : slot * 20));
        }
        return players;
    }

    private WorldSnapshot world(List<PlayerSnapshot> players) {
        return new WorldSnapshot(1, new ArenaSnapshot(960, 540,
                List.of(new PlatformSnapshot(0, 480, 960, 60))), players, List.of(),
                List.of(new NPCSnapshot(1, 400, 416, 42, 64, "PATROL", true, 0)));
    }

    private BufferedImage render(GamePanel panel) {
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private int[] corner(BufferedImage image, int slot) {
        int width = image.getWidth() / 2;
        int height = image.getHeight() / 4;
        int x = slot % 2 == 0 ? 0 : image.getWidth() - width;
        int y = slot < 2 ? 0 : image.getHeight() - height;
        return image.getRGB(x, y, width, height, null, 0, width);
    }

    private void save(BufferedImage image, String name) {
        try {
            Files.createDirectories(Path.of("target"));
            ImageIO.write(image, "png", Path.of("target/" + name + ".png").toFile());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void cornersStayFixedAcrossMovementOrderResizeAndClearDepartedPlayers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int width : new int[]{960, 640}) {
                GamePanel panel = new GamePanel();
                panel.setSize(width, width == 960 ? 540 : 360);
                try {
                    panel.setSnapshot(world(players(false, 168.75)));
                    panel.setLocalPlayerId(ids[2]);
                    BufferedImage initial = render(panel);
                    save(initial, "player-hud-" + width);
                    assertEquals(0, panel.getComponentCount(), "Painted cards must not introduce child controls");
                    List<PlayerSnapshot> reordered = players(true, 168.75);
                    Collections.reverse(reordered);
                    panel.setSnapshot(world(reordered));
                    BufferedImage moved = render(panel);
                    for (int slot = 0; slot < 4; slot++) {
                        assertArrayEquals(corner(initial, slot), corner(moved, slot),
                                "Corner " + slot + " must not follow position or snapshot order");
                    }
                    panel.setSnapshot(world(players(true, 205.5)));
                    BufferedImage stressed = render(panel);
                    assertFalse(Arrays.equals(corner(moved, 0), corner(stressed, 0)),
                            "Latest authoritative stress must visibly update its card");
                    for (int slot = 1; slot < 4; slot++) {
                        assertArrayEquals(corner(moved, slot), corner(stressed, slot));
                    }
                    panel.showNotification("Item collected — notification remains below the top cards");
                    save(render(panel), "player-hud-updated-" + width);
                    panel.clearGame();
                    panel.setSnapshot(world(List.of(players(false, 168.75).getFirst())));
                    BufferedImage departed = render(panel);
                    panel.setSnapshot(world(List.of()));
                    BufferedImage empty = render(panel);
                    assertArrayEquals(corner(empty, 1), corner(departed, 1), "Departed top-right card clears");
                    assertArrayEquals(corner(empty, 2), corner(departed, 2), "Departed local card clears");
                    assertArrayEquals(corner(empty, 3), corner(departed, 3));
                    panel.setSnapshot(world(players(false, 168.75)));
                    BufferedImage noLocal = render(panel);
                    assertFalse(Arrays.equals(corner(initial, 2), corner(noLocal, 2)),
                            "clearGame must clear the local-player indication");
                    panel.clearGame();
                    GamePanel blank = new GamePanel();
                    blank.setSize(panel.getSize());
                    assertArrayEquals(render(blank).getRGB(0, 0, panel.getWidth(), panel.getHeight(), null, 0, panel.getWidth()),
                            render(panel).getRGB(0, 0, panel.getWidth(), panel.getHeight(), null, 0, panel.getWidth()),
                            "clearGame removes all stale portraits, names and stress");
                    blank.removeNotify();
                } finally {
                    panel.clearGame();
                    panel.removeNotify();
                }
            }
        });
    }
}
