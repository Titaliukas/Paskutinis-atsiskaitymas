package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Image;
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
