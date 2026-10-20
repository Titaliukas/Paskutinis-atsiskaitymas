package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionProjectileTest {
    private final UUID playerId = UUID.randomUUID();

    private GameSession session(int count) {
        GameSession session = new GameSession(UUID.randomUUID(), NPCStrategyTest.arena());
        session.addPlayer(new Player(playerId, "Player"), 0, CharacterType.PATASIJUS);
        for (int id = 1; id <= count; id++) {
            session.spawnNPC(id, new Position(350, 416), 350, 351);
        }
        return session;
    }

    private void tick(GameSession session, int count) {
        for (int i = 0; i < count; i++) {
            session.advance(Map.of(), GameConstants.TICK_SECONDS);
        }
    }

    @Test
    void chaseMovesAndThrowsImmediatelyButOtherActivitiesAndStaleRequestsAreRejected() {
        GameSession session = session(1);
        NPCAction request = new NPCAction(0, 0, null, session.npcTargets().getFirst().id());
        assertFalse(session.resolveNPCThrow(1, request), "PATROL cannot throw");
        tick(session, 36);
        NPCView npc = session.npcs().getFirst();
        assertEquals(NPCActivity.CHASE, npc.activity());
        assertTrue(npc.state().velocityX() < 0);
        assertFalse(npc.state().throwReady());
        assertEquals(1, session.projectiles().size());
        assertEquals(ProjectileType.BOOK, session.projectiles().getFirst().type());
        assertFalse(session.resolveNPCThrow(1, request), "Cooldown rejects repeat request");
        assertFalse(session.resolveNPCThrow(99, request));
        assertFalse(session.resolveNPCThrow(1, new NPCAction(0, 0, null, 999)));
        tick(session, 1);
        assertEquals(1, session.projectiles().size());
        assertThrows(IllegalArgumentException.class, () -> new NPCAction(0, 0, 1, 1));
    }

    @Test
    void fullArenaConsumesCooldownAndDoesNotRetryImmediatelyWhenSpaceOpens() {
        GameSession session = session(9);
        tick(session, 36);
        assertEquals(8, session.projectiles().size());
        assertFalse(session.npcs().getLast().state().throwReady());
        for (int id = 1; id <= 8; id++) {
            session.removeNPC(id);
        }
        assertTrue(session.projectiles().isEmpty());
        tick(session, 1);
        assertTrue(session.projectiles().isEmpty());
        assertFalse(session.npcs().getFirst().state().throwReady());
    }

    @Test
    void disconnectAndNPCRemovalCleanUpProjectilesAndReconnectCannotReuseTarget() {
        GameSession session = session(1);
        tick(session, 36);
        int target = session.npcTargets().getFirst().id();
        var detached = session.projectiles();
        session.removePlayer(playerId);
        assertTrue(session.projectiles().isEmpty());
        assertEquals(1, detached.size());
        session.addPlayer(new Player(playerId, "Again"), 0, CharacterType.PATASIJUS);
        assertNotEquals(target, session.npcTargets().getFirst().id());
        assertFalse(session.resolveNPCThrow(1, new NPCAction(0, 0, null, target)));
        tick(session, 80);
        session.removeNPC(1);
        assertTrue(session.projectiles().isEmpty());
    }

    @Test
    void throwCooldownSurvivesStrategyReplacementAndVariantsAlternateOnlyOnSpawn() {
        NPC npc = NPCStrategyTest.npc(100);
        npc.setTargetId(7);
        NPCTarget target = new NPCTarget(7, 400, 416, 42, 64);
        npc.setActivityStrategy(new ChaseStrategy(1.5));
        assertTrue(npc.advance(NPCStrategyTest.arena(), target, 0.01).hasThrowRequest());
        assertEquals(ProjectileType.BOOK, npc.recordThrowAttempt());
        npc.recordProjectileSpawned();
        npc.setActivityStrategy(new FleeStrategy(1));
        assertFalse(npc.advance(NPCStrategyTest.arena(), target, 0.6).hasThrowRequest());
        npc.setActivityStrategy(new ChaseStrategy(1.5));
        assertFalse(npc.advance(NPCStrategyTest.arena(), target, 0.59).hasThrowRequest());
        assertTrue(npc.advance(NPCStrategyTest.arena(), target, 0.01).hasThrowRequest());
        assertEquals(ProjectileType.DIPLOMA, npc.recordThrowAttempt());
        assertThrows(IllegalStateException.class, npc::recordThrowAttempt);
        npc.advance(NPCStrategyTest.arena(), target, 1.2);
        assertEquals(ProjectileType.DIPLOMA, npc.recordThrowAttempt(), "Skipped spawn does not alternate variant");
        npc.recordProjectileSpawned();
        npc.advance(NPCStrategyTest.arena(), target, 1.2);
        assertEquals(ProjectileType.BOOK, npc.recordThrowAttempt());
    }
}
