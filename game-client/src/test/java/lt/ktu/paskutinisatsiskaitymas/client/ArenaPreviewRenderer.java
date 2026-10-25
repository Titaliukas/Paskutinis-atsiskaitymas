package lt.ktu.paskutinisatsiskaitymas.client;

import java.nio.file.Files;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import lt.ktu.paskutinisatsiskaitymas.protocol.JsonMessageCodec;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** Offscreen QA tool; consumes JSON produced by the real server mapper, with no domain dependency. */
public final class ArenaPreviewRenderer {
    private ArenaPreviewRenderer() { }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        JsonMessageCodec codec = new JsonMessageCodec();
        for (String type : new String[]{"combat", "training"}) {
            WorldSnapshot world = (WorldSnapshot) codec.decode(Files.readString(directory.resolve(type + ".json")));
            SwingUtilities.invokeAndWait(() -> {
                for (boolean debug : new boolean[]{false, true}) {
                    System.setProperty("game.debug.geometry", Boolean.toString(debug));
                    for (int[] size : new int[][]{{960, 540}, {640, 360}, {640, 400}}) {
                        GamePanel panel = new GamePanel();
                        panel.setSize(size[0], size[1]);
                        panel.setSnapshot(world);
                        panel.setLocalPlayerId(world.players().getFirst().playerId());
                        try {
                            String name = type + "-" + size[0] + "x" + size[1] + (debug ? "-debug" : "") + ".png";
                            BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
                            Graphics2D graphics = image.createGraphics();
                            try { panel.paint(graphics); } finally { graphics.dispose(); }
                            ImageIO.write(image, "png", directory.resolve(name).toFile());
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        } finally {
                            panel.clearGame();
                        }
                    }
                }
            });
        }
        System.out.println("GamePanel previews from server snapshots: " + directory.toAbsolutePath());
    }
}
