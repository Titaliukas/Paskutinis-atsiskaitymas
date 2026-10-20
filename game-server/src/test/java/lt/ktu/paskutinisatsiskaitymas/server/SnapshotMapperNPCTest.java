package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.CharacterType;
import lt.ktu.paskutinisatsiskaitymas.domain.GameConstants;
import lt.ktu.paskutinisatsiskaitymas.domain.GameSession;
import lt.ktu.paskutinisatsiskaitymas.domain.NPCActivity;
import lt.ktu.paskutinisatsiskaitymas.domain.Player;
import lt.ktu.paskutinisatsiskaitymas.protocol.JsonMessageCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotMapperNPCTest {
    @Test
    void mapsAllRealActivitiesAndAttackSequenceAtTwentyHzWithoutLeakingMutableState() throws Exception {
        GameSession session = GameSession.createDefault();
        session.removeNPC(2);
        session.addPlayer(new Player(UUID.randomUUID(), "Player"), 0, CharacterType.values()[0]);
        var initial = SnapshotMapper.map(0, session);
        EnumSet<NPCActivity> seen = EnumSet.noneOf(NPCActivity.class);
        JsonMessageCodec codec = new JsonMessageCodec();
        long observedSequence = 0;
        boolean sawProjectile = false;
        boolean sawSlow = false;
        for (int tick = 0; tick < 600; tick++) {
            if (tick % 3 == 0) {
                var snapshot = SnapshotMapper.map(tick, session);
                sawProjectile |= !snapshot.projectiles().isEmpty();
                sawSlow |= snapshot.players().getFirst().slowed();
                assertEquals(session.projectiles().stream().map(shot -> shot.id()).toList(),
                        snapshot.projectiles().stream().map(shot -> shot.id()).toList());
                for (int index = 0; index < snapshot.projectiles().size(); index++) {
                    var shot = snapshot.projectiles().get(index);
                    var domain = session.projectiles().get(index);
                    assertEquals(domain.type().name(), shot.projectileType());
                    assertEquals(domain.position().x(), shot.x());
                    assertEquals(domain.velocityX(), shot.velocityX());
                    assertEquals(domain.returning(), shot.returning());
                }
                assertEquals(snapshot, codec.decode(codec.encode(snapshot)));
                var npc = snapshot.npcs().getFirst();
                seen.add(NPCActivity.valueOf(npc.activity()));
                if (npc.attackAttemptSequence() > observedSequence) {
                    assertTrue(snapshot.players().getFirst().stressLevel() > 0);
                    observedSequence = npc.attackAttemptSequence();
                    assertEquals("FLEE", npc.activity(), "Successful ATTACK finishes before snapshot; sequence survives");
                }
            }
            session.advance(Map.of(), GameConstants.TICK_SECONDS);
        }
        // ATTACK is transient after a successful hit. The wire sequence preserves presentation feedback.
        assertTrue(seen.containsAll(EnumSet.of(NPCActivity.PATROL, NPCActivity.CHASE, NPCActivity.FLEE)));
        assertTrue(observedSequence > 0);
        assertTrue(sawProjectile);
        assertTrue(sawSlow);
        assertTrue(initial.projectiles().isEmpty());
        assertFalse(initial.players().getFirst().slowed());
        assertEquals(0, initial.npcs().getFirst().attackAttemptSequence());
        assertEquals(0, initial.players().getFirst().stressLevel());
        assertEquals(360, initial.npcs().getFirst().x());
    }
}