package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Image;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssetManagerTest {
    @Test
    void loadsBundledImagesAndSharesTheCache() {
        AssetManager assets = AssetManager.getInstance();
        assertSame(assets, AssetManager.getInstance());
        for (String key : List.of("SPEED_BOOST", "JUMP_BOOST", "SHIELD", "MARTY", "JUSTAS", "JURGIS", "GUOG")) {
            Image image = assets.getImage(key);
            assertNotNull(image, key);
            assertTrue(image.getWidth(null) > 0, key);
            assertTrue(image.getHeight(null) > 0, key);
            assertSame(image, AssetManager.getInstance().getImage(key), key);
        }
    }

    @Test
    void unknownAssetAllowsRenderingFallback() {
        assertNull(AssetManager.getInstance().getImage("UNKNOWN"));
    }
}
