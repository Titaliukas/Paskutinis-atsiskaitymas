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
import lt.ktu.paskutinisatsiskaitymas.protocol.CreateLobby;
import lt.ktu.paskutinisatsiskaitymas.protocol.ExaminatorSnapshot;
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
                    42, 64, false, slot == 1, moved ? 260 : 0, 100, 100,
                    slot == 0 ? firstStress : slot * 20, CreateLobby.CHARACTERS.get(slot), false));
        }
        return players;
    }

    private WorldSnapshot world(List<PlayerSnapshot> players) {
        return new WorldSnapshot(1, new ArenaSnapshot("combat", 960, 540,
                List.of(new PlatformSnapshot(0, 480, 960, 60))), players, List.of(),
                List.of(new NPCSnapshot(1, 400, 416, 42, 64, "PATROL", true, 0, false)), List.of(), List.of());
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

    private WorldSnapshot arenaWorld(String type, List<ExaminatorSnapshot> abilities, long tick) {
        double ground = type.equals("training") ? 448 : 421;
        List<PlayerSnapshot> roster = new ArrayList<>();
        for (int slot = 0; slot < 4; slot++) {
            roster.add(new PlayerSnapshot(ids[slot], slot, slot == 0 ? "Long Inspector Nickname" : "Player " + (slot + 1),
                    120 + slot * 220, ground - 64, 42, 64, true, false, 0, 100, 100,
                    slot * 23.5, CreateLobby.CHARACTERS.get(slot), false));
        }
        return new WorldSnapshot(tick, new ArenaSnapshot(type, 960, 540,
                List.of(new PlatformSnapshot(0, ground, 960, 540 - ground))), roster, List.of(), List.of(),
                List.of(), List.of(), abilities);
    }

    private ExaminatorSnapshot arriving(double y, double simulationTime) {
        return new ExaminatorSnapshot(ids[0], 480, y, "FLY", 30, 0.8, simulationTime, 0,
                487, y - 3, 487, y - 3, List.of());
    }

    @Test
    void fourStatusRowsAndOffscreenEntryFitBothArenasAtNormalSmallAndLetterboxedSizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (String arena : List.of("training", "combat")) {
                for (int[] size : new int[][]{{960, 540}, {640, 360}, {640, 400}}) {
                    GamePanel panel = new GamePanel();
                    panel.setSize(size[0], size[1]);
                    String suffix = arena + "-" + size[0] + "x" + size[1];
                    try {
                        panel.setLocalPlayerId(ids[2]);
                        var idle = List.of(HudCompositeTest.ability(ids[0], "IDLE", 0),
                                HudCompositeTest.ability(ids[1], "IDLE", 23.01),
                                HudCompositeTest.ability(ids[2], "IDLE", 8),
                                HudCompositeTest.ability(ids[3], "IDLE", 0));
                        panel.setSnapshot(arenaWorld(arena, idle, 1));
                        BufferedImage absent = render(panel);
                        save(absent, "examinator-idle-" + suffix);
                        panel.clearGame();
                        panel.setLocalPlayerId(ids[2]);
                        var offscreen = new ArrayList<>(idle);
                        offscreen.set(0, arriving(-64, 3));
                        panel.setSnapshot(arenaWorld(arena, offscreen, 2));
                        BufferedImage beforeEntry = render(panel);
                        save(beforeEntry, "examinator-arrival-offscreen-" + suffix);
                        // The center strip excludes HUD cards, whose authoritative status changes during entry.
                        assertArrayEquals(absent.getRGB(size[0] / 2 - 25, 0, 50, size[1], null, 0, 50),
                                beforeEntry.getRGB(size[0] / 2 - 25, 0, 50, size[1], null, 0, 50),
                                "An offscreen launch cannot paint a head inside the arena or its margins");
                        offscreen.set(0, arriving(-10, 3.1));
                        panel.setSnapshot(arenaWorld(arena, offscreen, 3));
                        BufferedImage entry = render(panel);
                        save(entry, "examinator-arrival-entry-" + suffix);
                        double scale = Math.min(size[0] / 960.0, size[1] / 540.0);
                        int top = (int) Math.floor((size[1] - 540 * scale) / 2);
                        if (top > 0) {
                            assertArrayEquals(absent.getRGB(size[0] / 2 - 25, 0, 50, top, null, 0, 50),
                                    entry.getRGB(size[0] / 2 - 25, 0, 50, top, null, 0, 50),
                                    "Partial entry must be clipped at the displayed arena boundary");
                        }
                        assertFalse(Arrays.equals(absent.getRGB(size[0] / 2 - 25, top, 50, 20, null, 0, 50),
                                entry.getRGB(size[0] / 2 - 25, top, 50, 20, null, 0, 50)),
                                "The head becomes visible as it crosses the arena boundary");
                        var statuses = List.of(HudCompositeTest.ability(ids[3], "INSPECT", 29),
                                HudCompositeTest.ability(ids[1], "IDLE", 23.01),
                                HudCompositeTest.ability(ids[0], "IDLE", 0),
                                HudCompositeTest.ability(ids[2], "AIM", 0));
                        panel.clearGame();
                        panel.setLocalPlayerId(ids[2]);
                        panel.setSnapshot(arenaWorld(arena, statuses, 4));
                        save(render(panel), "examinator-hud-" + suffix);
                        assertEquals(0, panel.getComponentCount());
                        panel.clearGame();
                        panel.setSnapshot(arenaWorld(arena, List.of(), 0));
                        save(render(panel), "examinator-hud-missing-" + suffix);
                    } finally {
                        panel.clearGame();
                        panel.removeNotify();
                    }
                }
            }
        });
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