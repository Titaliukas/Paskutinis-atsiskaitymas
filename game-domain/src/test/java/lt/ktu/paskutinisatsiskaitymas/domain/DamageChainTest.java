package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageChainTest {
    private final DamageHandler chain = DamageChain.standard();

    private GameCharacter character() {
        return new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "P"),
                0, new Position(180, 416), true, CharacterType.PATASIJUS);
    }

    @Test
    void shieldStopsTheChainBeforeStressAndHealth() {
        GameCharacter c = character();
        c.applyShield(5);
        assertFalse(DamageChain.apply(chain, c, 10));
        assertEquals(0, c.state().stressLevel());
        assertEquals(100, c.state().health());
    }

    @Test
    void normalDamageRaisesStressAndLowersHealth() {
        GameCharacter c = character();
        assertTrue(DamageChain.apply(chain, c, 12));
        assertEquals(12, c.state().stressLevel());
        assertEquals(88, c.state().health());
    }

    @Test
    void negligibleDamageStopsAtStressAndChangesNothing() {
        GameCharacter c = character();
        DamageChain.apply(chain, c, 20);
        assertFalse(DamageChain.apply(chain, c, Double.MIN_VALUE));
        assertEquals(80, c.state().health());
        assertEquals(20, c.state().stressLevel());
    }

    @Test
    void lethalDamageEndsInRespawnWithFullHealth() {
        GameCharacter c = character();
        assertTrue(DamageChain.apply(chain, c, 100));
        assertEquals(100, c.state().health());
        assertEquals(1, c.respawnSequence());
    }

    @Test
    void invalidDamageIsRejectedBeforeEnteringTheChain() {
        GameCharacter c = character();
        for (double bad : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> DamageChain.apply(chain, c, bad));
        }
        assertEquals(100, c.state().health());
    }

    @Test
    void publicTakeDamageUsesTheSameChain() {
        GameCharacter c = character();
        assertTrue(c.takeDamage(10));
        assertEquals(10, c.state().stressLevel());
        c.applyShield(5);
        assertFalse(c.takeDamage(10));
        assertEquals(10, c.state().stressLevel());
    }

    @Test
    void anExtraHandlerCanBeInsertedWithoutChangingTheOthers() {
        DamageHandler head = new ShieldHandler();
        head.linkWith(new ProneHandler())
            .linkWith(new DamageHandler() {          // papildomas "armor" elementas
                @Override
                protected boolean process(DamageContext context) {
                    context.scale(0.8);
                    return false;
                }
            })
            .linkWith(new StressHandler())
            .linkWith(new HealthHandler())
            .linkWith(new RespawnHandler());
        GameCharacter c = character();
        assertTrue(DamageChain.apply(head, c, 10));
        assertEquals(8, c.state().stressLevel(), 1e-9);
        assertEquals(92, c.state().health());
    }
    @Test
    void proneHalvesDamageBeforeStressAndHealth() {
        GameCharacter c = character();
        c.setProne(true);
        assertTrue(DamageChain.apply(chain, c, 10));
        assertEquals(5, c.state().stressLevel(), 1e-9);
        assertEquals(95, c.state().health());
    }

    @Test
    void shieldBlocksBeforeProneScalingIsRelevant() {
        GameCharacter c = character();
        c.setProne(true);
        c.applyShield(5);
        assertFalse(DamageChain.apply(chain, c, 10));
        assertEquals(0, c.state().stressLevel());
        assertEquals(100, c.state().health());
    }

    @Test
    void proneDamageRoundsHealthLossUp() {
        GameCharacter c = character();
        c.setProne(true);
        assertTrue(DamageChain.apply(chain, c, 5));   // 2.5 -> ceil = 3
        assertEquals(2.5, c.state().stressLevel(), 1e-9);
        assertEquals(97, c.state().health());
    }
}