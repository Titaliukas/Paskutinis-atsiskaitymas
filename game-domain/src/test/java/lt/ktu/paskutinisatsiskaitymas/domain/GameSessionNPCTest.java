package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionNPCTest {
    private final UUID playerId = UUID.randomUUID();

    private GameSession session() {
        GameSession session = new GameSession(UUID.randomUUID(), NPCStrategyTest.arena());
        session.addPlayer(new Player(playerId, "Player"), 0);
        session.spawnNPC(1, new Position(235, 416), 235, 236);
        return session;
    }

    private void tick(GameSession session, int count) {
        for (int i = 0; i < count; i++) {
            session.advance(Map.of(), GameConstants.TICK_SECONDS);
        }
    }

    @Test
    void successfulHitChangesStressAndFleesAndRejectsRepeatedOrStaleRequests() {
        GameSession session = session();
        tick(session, 37);
        assertEquals(GameConstants.NPC_ATTACK_DAMAGE, session.characters().getFirst().stressLevel());
        NPCView view = session.npcs().getFirst();
        assertEquals(NPCActivity.FLEE, view.activity());
        assertEquals(1, view.state().attackAttemptSequence());
        assertFalse(view.state().attackReady());
        assertFalse(session.resolveNPCAction(1, new NPCAction(0, 0, 1)));
        assertFalse(session.resolveNPCAction(1, new NPCAction(0, 0, 999)));
        assertFalse(session.resolveNPCAction(99, new NPCAction(0, 0, 1)));
        assertEquals(GameConstants.NPC_ATTACK_DAMAGE, session.characters().getFirst().stressLevel());
    }

    @Test
    void shieldBlocksDamageButConsumesCooldownAndProducesAttemptFeedback() {
        GameSession session = session();
        session.spawnItemIfDue(6, new Random(0) {
            @Override public int nextInt(int bound) { return ItemType.SHIELD.ordinal(); }
            @Override public double nextDouble() { return 180.0 / (960 - 42); }
        });
        tick(session, 37);
        assertTrue(session.characters().getFirst().shielded());
        assertEquals(0, session.characters().getFirst().stressLevel());
        assertEquals(NPCActivity.ATTACK, session.npcs().getFirst().activity());
        assertEquals(1, session.npcs().getFirst().state().attackAttemptSequence());
        tick(session, 60);
        assertEquals(1, session.npcs().getFirst().state().attackAttemptSequence());
        tick(session, 14);
        assertEquals(2, session.npcs().getFirst().state().attackAttemptSequence());
        assertEquals(0, session.characters().getFirst().stressLevel());
        assertFalse(session.resolveNPCAction(1, new NPCAction(0, 0, 1)));
    }

    @Test
    void disconnectClearsTargetsAndReconnectAllocatesFreshNumericIdentity() {
        GameSession session = session();
        tick(session, 36);
        int oldTarget = session.npcTargets().getFirst().id();
        assertEquals(oldTarget, session.npcs().getFirst().state().targetId());
        session.removePlayer(playerId);
        assertTrue(session.npcTargets().isEmpty());
        assertNull(session.npcs().getFirst().state().targetId());
        assertEquals(NPCActivity.PATROL, session.npcs().getFirst().activity());
        assertFalse(session.resolveNPCAction(1, new NPCAction(0, 0, oldTarget)));
        session.addPlayer(new Player(playerId, "Reconnected"), 0);
        assertTrue(session.npcTargets().getFirst().id() > oldTarget);
        tick(session, 36);
        assertEquals(session.npcTargets().getFirst().id(), session.npcs().getFirst().state().targetId());
    }

    @Test
    void spawningAndRemovalKeepBothCollectionsConsistentAndViewsDetached() {
        GameSession session = session();
        var before = session.npcs();
        assertEquals(before.stream().map(view -> view.state().id()).toList(), session.npcControllerIds());
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        assertThrows(IllegalArgumentException.class, () -> session.spawnNPC(1, new Position(300, 416), 200, 400));
        assertThrows(IllegalArgumentException.class, () -> session.spawnNPC(2, new Position(300, 416), 200, 1000));
        session.removeNPC(1);
        assertTrue(session.npcs().isEmpty());
        assertTrue(session.npcControllerIds().isEmpty());
        assertEquals(1, before.size());
        session.spawnNPC(2, new Position(300, 416), 200, 400);
        assertEquals(2, session.npcControllerIds().getFirst());
        assertEquals(1, before.getFirst().state().id());
    }

    @Test
    void rejectsOutOfRangeRequestsUsingCurrentPreciseGeometry() {
        GameSession session = session();
        tick(session, 36);
        session.advance(Map.of(playerId, new MovementInput(true, false, false)), 0.4);
        double before = session.characters().getFirst().stressLevel();
        assertFalse(session.resolveNPCAction(1, new NPCAction(0, 0, 1)));
        assertEquals(before, session.characters().getFirst().stressLevel());
    }

    @Test
    void emptySessionRejectsInvalidDeltaBeforeMutatingAnythingAndDefaultSpawnsNPCs() {
        GameSession empty = new GameSession(UUID.randomUUID(), NPCStrategyTest.arena());
        for (double seconds : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> empty.advance(Map.of(), seconds));
        }
        GameSession normal = GameSession.createDefault();
        assertEquals(2, normal.npcs().size());
        tick(normal, 200);
        assertTrue(normal.npcs().stream().allMatch(view -> view.activity() == NPCActivity.PATROL));
    }

    @Test
    void productionNPCsDetectPlayersInSlotsTwoAndThreeUsingExistingTargets() {
        for (int slot : new int[]{2, 3}) {
            GameSession session = GameSession.createDefault();
            UUID id = UUID.randomUUID();
            session.addPlayer(new Player(id, "Player " + slot), slot);
            for (int tick = 0; tick < 80; tick++) {
                session.advance(Map.of(id, new MovementInput(slot == 3, slot == 2, false)), GameConstants.TICK_SECONDS);
            }
            Integer target = session.npcTargets().getFirst().id();
            assertTrue(session.npcs().stream().anyMatch(view -> target.equals(view.state().targetId())
                    && view.activity() != NPCActivity.PATROL), "NPC must pursue eligible slot " + slot);
            session.removePlayer(id);
            assertTrue(session.npcTargets().isEmpty());
            assertTrue(session.npcs().stream().allMatch(view -> view.state().targetId() == null));
        }
    }

    @Test
    void characterDamageIsActualAndShieldAwareAndRejectsInvalidDamage() {
        GameCharacter character = new GameCharacter(UUID.randomUUID(), new Player(playerId, "Player"),
                0, new Position(180, 416), true);
        assertTrue(character.takeDamage(10));
        assertEquals(10, character.state().stressLevel());
        character.applyShield(1);
        assertFalse(character.takeDamage(10));
        assertEquals(10, character.state().stressLevel());
        character.advance(MovementInput.NONE, NPCStrategyTest.arena(), 1);
        assertTrue(character.takeDamage(10));
        assertEquals(20, character.state().stressLevel());
        assertFalse(character.takeDamage(Double.MIN_VALUE), "Success must mean actual state changed");
        assertEquals(20, character.state().stressLevel());
        for (double damage : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> character.takeDamage(damage));
        }
    }
}
