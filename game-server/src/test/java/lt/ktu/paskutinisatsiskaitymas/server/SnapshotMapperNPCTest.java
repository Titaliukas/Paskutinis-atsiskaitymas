package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.EnumSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import lt.ktu.paskutinisatsiskaitymas.domain.Arena;
import lt.ktu.paskutinisatsiskaitymas.domain.ArenaType;
import lt.ktu.paskutinisatsiskaitymas.domain.NPCSpawn;
import lt.ktu.paskutinisatsiskaitymas.domain.Platform;
import lt.ktu.paskutinisatsiskaitymas.domain.Position;
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
        Arena arena = new Arena("Mapper Strategy fixture", ArenaType.TRAINING, 960, 540,
                List.of(new Platform(UUID.randomUUID(), new Position(0, 480), 960, 60)),
                List.of(new Position(180, 416), new Position(738, 416),
                        new Position(60, 416), new Position(858, 416)),
                List.of(new NPCSpawn(1, new Position(360, 416), 300, 420)), 480, Optional.empty());
        GameSession session = new GameSession(UUID.randomUUID(), arena);
        session.addPlayer(new Player(UUID.randomUUID(), "Player"), 0, CharacterType.values()[0]);
        var initial = SnapshotMapper.map(0, session);
        EnumSet<NPCActivity> seen = EnumSet.noneOf(NPCActivity.class);
        JsonMessageCodec codec = new JsonMessageCodec();
        long observedSequence = 0;
        boolean sawProjectile = false;
        boolean sawSlow = false;
        // The complete cycle and projectile hit occur before 5 seconds. Keep this focused scenario
        // before the 6-second random item spawn, which can legitimately shield a later attack.
        for (int tick = 0; tick < 5 * GameConstants.TICK_RATE; tick++) {
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
        assertTrue(session.activeItems().isEmpty(), "Strategy mapper scenario excludes random item effects");
        assertTrue(initial.projectiles().isEmpty());
        assertFalse(initial.players().getFirst().slowed());
        assertEquals(0, initial.npcs().getFirst().attackAttemptSequence());
        assertEquals(0, initial.players().getFirst().stressLevel());
        assertEquals(360, initial.npcs().getFirst().x());
    }
}