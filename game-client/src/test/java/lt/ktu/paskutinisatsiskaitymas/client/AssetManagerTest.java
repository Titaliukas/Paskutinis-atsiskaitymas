package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssetManagerTest {
    @Test
    void loadsBundledImagesAndSharesTheCache() {
        AssetManager assets = AssetManager.getInstance();
        assertSame(assets, AssetManager.getInstance());
        for (String key : List.of("SPEED_BOOST", "JUMP_BOOST", "SHIELD", "MARTY", "MARTY_PORTRAIT", "JUSTAS", "JURGIS", "GUOG",
                "NPC_PATROL", "NPC_CHASE", "NPC_ATTACK", "NPC_FLEE")) {
            Image image = assets.getImage(key);
            assertNotNull(image, key);
            assertTrue(image.getWidth(null) > 0, key);
            assertTrue(image.getHeight(null) > 0, key);
            assertSame(image, AssetManager.getInstance().getImage(key), key);
        }
    }

    @Test
    void cachedPortraitRetainsAlphaAndFramesUpperBodyWithoutChangingWorldSprite() {
        var assets = AssetManager.getInstance();
        var source = (BufferedImage) assets.getImage("MARTY");
        var portrait = (BufferedImage) assets.getImage("MARTY_PORTRAIT");
        assertTrue(portrait.getColorModel().hasAlpha());
        assertTrue(portrait.getHeight() > 0 && portrait.getHeight() < source.getHeight() * 0.4);
        assertTrue(portrait.getWidth() > 0 && portrait.getWidth() <= source.getWidth());
        assertSame(portrait, assets.getImage("MARTY_PORTRAIT"));
        assertSame(source, assets.getImage("MARTY"));
    }

    @Test
    void unknownAssetAllowsRenderingFallback() {
        assertNull(AssetManager.getInstance().getImage("UNKNOWN"));
    }
}
