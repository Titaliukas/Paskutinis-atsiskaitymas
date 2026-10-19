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
        for (String key : List.of("SPEED_BOOST", "JUMP_BOOST", "SHIELD", "PATASIJUS", "PATASIJUS_PORTRAIT",
                "VACIJUS", "VACIJUS_PORTRAIT", "JURGELINIS", "JURGELINIS_PORTRAIT", "GOGIS", "GOGIS_PORTRAIT",
                "PATASIJUS_PROJECTILE", "PATASIJUS_MELEE", "VACIJUS_PROJECTILE", "VACIJUS_MELEE", "GOGIS_PROJECTILE",
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
        var source = (BufferedImage) assets.getImage("PATASIJUS");
        var portrait = (BufferedImage) assets.getImage("PATASIJUS_PORTRAIT");
        assertTrue(portrait.getColorModel().hasAlpha());
        assertTrue(portrait.getWidth() > 0 && portrait.getWidth() <= source.getWidth());
        assertTrue(portrait.getHeight() > 0 && portrait.getHeight() < source.getHeight() * 0.4);
        assertSame(portrait, assets.getImage("PATASIJUS_PORTRAIT"));
        assertSame(source, assets.getImage("PATASIJUS"));
    }

    @Test
    void characterPortraitsUseSquareConsistentFrames() {
        AssetManager assets = AssetManager.getInstance();
        for (String character : List.of("PATASIJUS", "VACIJUS", "JURGELINIS", "GOGIS")) {
            var portrait = (BufferedImage) assets.getImage(character + "_PORTRAIT");
            assertEquals(portrait.getWidth(), portrait.getHeight(), character);
            assertTrue(portrait.getWidth() >= 200, character);
        }
    }

    @Test
    void unknownAssetAllowsRenderingFallback() {
        assertNull(AssetManager.getInstance().getImage("UNKNOWN"));
    }
}
