package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** One shared image cache per client JVM. Callers must treat cached images as read-only. */
public final class AssetManager {
    private static final System.Logger LOG = System.getLogger(AssetManager.class.getName());
    private static final AssetManager INSTANCE = new AssetManager();
    private final Map<String, Image> images;

    private AssetManager() {
        Map<String, Image> loaded = new HashMap<>();
        load(loaded, "SPEED_BOOST", "assets/SPEED.png");
        load(loaded, "JUMP_BOOST", "assets/JUMP.png");
        load(loaded, "SHIELD", "assets/SHIELD.png");
        load(loaded, "MARTY", "assets/MARTY.png");
        load(loaded, "JUSTAS", "assets/JUSTAS.png");
        load(loaded, "JURGIS", "assets/JURGIS.png");
        load(loaded, "GUOG", "assets/GUOG.png");
        load(loaded, "NPC_PATROL", "assets/NPC_PATROL.png");
        load(loaded, "NPC_CHASE", "assets/NPC_CHASE.png");
        load(loaded, "NPC_ATTACK", "assets/NPC_ATTACK.png");
        load(loaded, "NPC_FLEE", "assets/NPC_FLEE.png");
        // MARTY is also the world-player sprite. Portrait framing is presentation metadata only.
        cachePortrait(loaded, "MARTY", "MARTY_PORTRAIT", 0.28);
        images = Map.copyOf(loaded);
    }

    /** Initializes the cache on first access; normal startup calls this before creating Swing UI. */
    public static AssetManager getInstance() {
        return INSTANCE;
    }

    /** Returns the shared image, or null for an unknown or unavailable asset so rendering can fall back. */
    public Image getImage(String key) {
        return images.get(key);
    }

    /** Crop MARTY's head/shoulders once, relative to its nontransparent bounds; never crop during painting. */
    private static void cachePortrait(Map<String, Image> loaded, String sourceKey, String portraitKey,
            double upperFraction) {
        if (!(loaded.get(sourceKey) instanceof BufferedImage source)) {
            return;
        }
        int left = source.getWidth();
        int top = source.getHeight();
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) != 0) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right >= left && bottom >= top) {
            int height = Math.max(1, (int) Math.round((bottom - top + 1) * upperFraction));
            loaded.put(portraitKey, source.getSubimage(left, top, right - left + 1, height));
        }
    }

    private static void load(Map<String, Image> loaded, String key, String resourcePath) {
        try (var stream = AssetManager.class.getResourceAsStream("/" + resourcePath)) {
            if (stream == null) {
                LOG.log(System.Logger.Level.WARNING, "Image not found: {0}", resourcePath);
                return;
            }
            Image image = ImageIO.read(stream);
            if (image == null) {
                LOG.log(System.Logger.Level.WARNING, "Unsupported image: {0}", resourcePath);
                return;
            }
            loaded.put(key, image);
        } catch (IOException exception) {
            LOG.log(System.Logger.Level.WARNING, "Cannot load image: " + resourcePath, exception);
        }
    }
}
