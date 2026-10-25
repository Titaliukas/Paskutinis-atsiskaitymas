package lt.ktu.paskutinisatsiskaitymas.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.CharacterType;
import lt.ktu.paskutinisatsiskaitymas.domain.GameConstants;
import lt.ktu.paskutinisatsiskaitymas.domain.GameSession;
import lt.ktu.paskutinisatsiskaitymas.domain.Player;
import lt.ktu.paskutinisatsiskaitymas.protocol.JsonMessageCodec;

/** Reproducible visual QA data from actual builders, sessions, one owner tick, and SnapshotMapper. */
public final class ArenaPreviewData {
    private ArenaPreviewData() { }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        JsonMessageCodec codec = new JsonMessageCodec();
        for (String type : new String[]{"combat", "training"}) {
            GameSession session = new GameSession(UUID.randomUUID(), ArenaSelection.create(type, null));
            for (int slot = 0; slot < 4; slot++) {
                session.addPlayer(new Player(new UUID(0, slot + 1), "Player " + (slot + 1)),
                        slot, CharacterType.values()[slot]);
            }
            session.advance(Map.of(), GameConstants.TICK_SECONDS);
            Files.writeString(directory.resolve(type + ".json"), codec.encode(SnapshotMapper.map(1, session)));
        }
    }
}
