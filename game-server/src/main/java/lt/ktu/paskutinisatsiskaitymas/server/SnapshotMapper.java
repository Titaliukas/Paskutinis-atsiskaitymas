package lt.ktu.paskutinisatsiskaitymas.server;

import lt.ktu.paskutinisatsiskaitymas.domain.GameConstants;
import lt.ktu.paskutinisatsiskaitymas.domain.GameSession;
import lt.ktu.paskutinisatsiskaitymas.protocol.ArenaSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlatformSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.PlayerSnapshot;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** Maps loop-owned domain state into detached transport DTOs. */
final class SnapshotMapper {
    private SnapshotMapper() { }

    static WorldSnapshot map(long tick, GameSession session) {
        ArenaSnapshot arena = new ArenaSnapshot(session.arena().width(), session.arena().height(),
                session.arena().platforms().stream()
                        .map(platform -> new PlatformSnapshot(platform.position().x(), platform.position().y(),
                                platform.width(), platform.height()))
                        .toList());
        var players = session.characters().stream()
                .map(character -> new PlayerSnapshot(character.playerId(), character.slot(), character.nickname(),
                        character.x(), character.y(), GameConstants.PLAYER_WIDTH,
                        GameConstants.PLAYER_HEIGHT, character.grounded()))
                .toList();
        return new WorldSnapshot(tick, arena, players);
    }
}
