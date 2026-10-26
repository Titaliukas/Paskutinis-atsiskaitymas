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
        load(loaded, "ARENA_COMBAT", "assets/combat_arena_v2.png");
        load(loaded, "ARENA_TRAINING", "assets/training_arena_v2.png");
        load(loaded, "SPEED_BOOST", "assets/SPEED.png");
        load(loaded, "JUMP_BOOST", "assets/JUMP.png");
        load(loaded, "SHIELD", "assets/SHIELD.png");
        load(loaded, "PATASIJUS", "assets/PATASIJUS.png");
        load(loaded, "VACIJUS", "assets/VACIJUS.png");
        load(loaded, "JURGELINIS", "assets/JURGELINIS.png");
        load(loaded, "GOGIS", "assets/GOGIS.png");
        load(loaded, "PATASIJUS_PROJECTILE", "assets/PATASIJUS_PROJECTILE.png");
        load(loaded, "PATASIJUS_MELEE", "assets/PATASIJUS_MELEE.png");
        load(loaded, "VACIJUS_PROJECTILE", "assets/VACIJUS_PROJECTILE.png");
        load(loaded, "VACIJUS_MELEE", "assets/VACIJUS_MELEE.png");
        load(loaded, "GOGIS_PROJECTILE", "assets/GOGIS_PROJECTILE.png");
        load(loaded, "GOGIS_MELEE", "assets/GOGIS_MELEE.png");
        load(loaded, "JURGELINIS_PROJECTILE", "assets/JURGELINIS_PROJECTILE.png");
        load(loaded, "JURGELINIS_MELEE", "assets/JURGELINIS_MELEE.png");
        load(loaded, "NPC_PATROL", "assets/NPC_PATROL.png");
        load(loaded, "NPC_CHASE", "assets/NPC_CHASE.png");
        load(loaded, "NPC_ATTACK", "assets/NPC_ATTACK.png");
        load(loaded, "NPC_FLEE", "assets/NPC_FLEE.png");
        // Square head-and-shoulders crops normalize the differently sized source sprites.
        cachePortrait(loaded, "PATASIJUS", "PATASIJUS_PORTRAIT", 0.165, 0, 0.660);
        cachePortrait(loaded, "VACIJUS", "VACIJUS_PORTRAIT", 0.280, 0.026, 0.391);
        cachePortrait(loaded, "JURGELINIS", "JURGELINIS_PORTRAIT", 0.147, 0, 0.697);
        cachePortrait(loaded, "GOGIS", "GOGIS_PORTRAIT", 0.376, 0, 0.254);
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

    /** Selects by the validated transport identity; never by a display name or domain type. */
    public Image arenaBackground(String arenaType) {
        return switch (arenaType) {
            case "combat" -> getImage("ARENA_COMBAT");
            case "training" -> getImage("ARENA_TRAINING");
            default -> null;
        };
    }

    /** Crop a normalized square portrait once; never crop during painting. */
    private static void cachePortrait(Map<String, Image> loaded, String sourceKey, String portraitKey,
            double leftFraction, double topFraction, double sizeFraction) {
        if (!(loaded.get(sourceKey) instanceof BufferedImage source)) {
            return;
        }
        int unit = Math.min(source.getWidth(), source.getHeight());
        int size = Math.max(1, (int) Math.round(unit * sizeFraction));
        int left = (int) Math.round(unit * leftFraction);
        int top = (int) Math.round(unit * topFraction);
        loaded.put(portraitKey, source.getSubimage(left, top, size, size));
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
