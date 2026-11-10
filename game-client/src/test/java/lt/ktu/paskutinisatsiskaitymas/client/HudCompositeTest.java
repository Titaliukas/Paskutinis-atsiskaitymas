package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.protocol.ArenaSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlatformSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlayerSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudCompositeTest {
    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;

    private static WorldSnapshot world(PlayerSnapshot... players) {
        return new WorldSnapshot(1, new ArenaSnapshot("training", 960, 540,
                List.of(new PlatformSnapshot(0, 448, 960, 92))), List.of(players), List.of(), List.of(),
                List.of(), List.of());
    }

    private static PlayerSnapshot player(int slot) {
        return new PlayerSnapshot(new UUID(0, slot + 1), slot, "Player " + slot, 100 + slot * 150, 384, 42, 64,
                true, false, 0, 100, 100, 0, "PATASIJUS", false);
    }

    private static BufferedImage render(HudElement element, WorldSnapshot world, String notification) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            element.draw(g, new HudContext(WIDTH, HEIGHT, world, null, notification, AssetManager.getInstance()));
        } finally {
            g.dispose();
        }
        return image;
    }

    private static boolean paints(BufferedImage image, int x, int y, int width, int height) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                if (image.getRGB(column, row) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Leaf that only records the order in which it was drawn. */
    private static final class Recorder implements HudElement {
        private final String name;
        private final List<String> log;

        Recorder(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public void draw(Graphics2D g, HudContext context) {
            log.add(name);
        }
    }

    @Test
    void hudIsATreeOfTwoCardGroupsAndANotification() {
        HudGroup root = Hud.create();
        assertEquals("Hud", root.name());
        assertEquals(3, root.children().size());
        HudGroup top = assertInstanceOf(HudGroup.class, root.children().get(0));
        HudGroup bottom = assertInstanceOf(HudGroup.class, root.children().get(1));
        assertInstanceOf(NotificationElement.class, root.children().get(2));
        assertEquals(List.of(0, 1), top.children().stream()
                .map(child -> ((PlayerCardElement) child).slot()).toList());
        assertEquals(List.of(2, 3), bottom.children().stream()
                .map(child -> ((PlayerCardElement) child).slot()).toList());
    }

    @Test
    void groupDrawsChildrenInOrderAndTreatsNestedGroupsLikeLeaves() {
        List<String> log = new ArrayList<>();
        HudGroup inner = new HudGroup("inner").add(new Recorder("b", log)).add(new Recorder("c", log));
        HudElement root = new HudGroup("root").add(new Recorder("a", log)).add(inner).add(new Recorder("d", log));
        render(root, world(), null);
        assertEquals(List.of("a", "b", "c", "d"), log);
    }

    @Test
    void groupManagesChildrenSafely() {
        List<String> log = new ArrayList<>();
        HudGroup group = new HudGroup("group");
        Recorder child = new Recorder("x", log);
        group.add(child);
        assertThrows(NullPointerException.class, () -> group.add(null));
        assertThrows(IllegalArgumentException.class, () -> group.add(group));
        List<HudElement> copy = group.children();
        assertThrows(UnsupportedOperationException.class, () -> copy.add(child));
        group.remove(child);
        assertTrue(group.children().isEmpty());
        assertEquals(List.of(), log);
        assertThrows(IllegalArgumentException.class, () -> new PlayerCardElement(4));
    }

    @Test
    void emptySlotsAndNoNotificationPaintNothing() {
        assertFalse(paints(render(Hud.create(), world(), null), 0, 0, WIDTH, HEIGHT));
    }

    @Test
    void eachCardAppearsOnlyInItsOwnCornerWhoseSlotIsOccupied() {
        int cardWidth = 248;
        int[][] corners = {{0, 0}, {WIDTH - cardWidth, 0}, {0, HEIGHT - 80}, {WIDTH - cardWidth, HEIGHT - 80}};
        for (int slot = 0; slot < 4; slot++) {
            BufferedImage image = render(Hud.create(), world(player(slot)), null);
            for (int other = 0; other < 4; other++) {
                assertEquals(other == slot, paints(image, corners[other][0], corners[other][1], cardWidth, 80),
                        "slot " + slot + " must paint only corner " + slot + ", checked corner " + other);
            }
        }
    }

    @Test
    void cardsDoNotDependOnPlayerListOrderAndDepartedPlayersDisappear() {
        BufferedImage ordered = render(Hud.create(), world(player(0), player(1), player(2), player(3)), null);
        BufferedImage reversed = render(Hud.create(), world(player(3), player(2), player(1), player(0)), null);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                assertEquals(ordered.getRGB(x, y), reversed.getRGB(x, y));
            }
        }
        BufferedImage onlyFirst = render(Hud.create(), world(player(0)), null);
        assertFalse(paints(onlyFirst, WIDTH - 248, 0, 248, 80), "Departed top-right card clears");
        assertFalse(paints(onlyFirst, WIDTH - 248, HEIGHT - 80, 248, 80), "Departed bottom-right card clears");
    }

    @Test
    void notificationIsDrawnByItsOwnLeafOnlyWhenTextExists() {
        HudElement leaf = new NotificationElement();
        assertFalse(paints(render(leaf, world(), null), 0, 0, WIDTH, HEIGHT));
        assertTrue(paints(render(leaf, world(), "Alibaba picked up SHIELD"), 0, 0, WIDTH, HEIGHT));
    }
}