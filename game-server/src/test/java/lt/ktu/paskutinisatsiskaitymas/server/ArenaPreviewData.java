package lt.ktu.paskutinisatsiskaitymas.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import lt.ktu.paskutinisatsiskaitymas.domain.Arena;
import lt.ktu.paskutinisatsiskaitymas.domain.Position;
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
        Arena combat = ArenaSelection.create("combat", null);
        renderData(directory, "training", ArenaSelection.create("training", null));
        renderData(directory, "combat", combat);
        String[] names = {"canopy", "service", "roof", "left-opening", "right-opening"};
        // Derive the surfaces from the real product; no independent client coordinates or fake wire poses.
        var canopy = combat.platforms().get(1);
        var service = combat.platforms().get(2);
        var roof = combat.platforms().get(0);
        Position[] poses = {new Position(canopy.left() + 67, canopy.top() - GameConstants.PLAYER_HEIGHT),
            new Position(service.left() + 30, service.top() - GameConstants.PLAYER_HEIGHT),
            new Position(roof.left() + 405, roof.top() - GameConstants.PLAYER_HEIGHT),
            new Position(0, combat.groundY() - GameConstants.PLAYER_HEIGHT),
            new Position(combat.width() - GameConstants.PLAYER_WIDTH, combat.groundY() - GameConstants.PLAYER_HEIGHT)};
        for (int i = 0; i < names.length; i++) {
            List<Position> spawns = new ArrayList<>(combat.playerSpawns());
            spawns.set(0, poses[i]);
            Arena poseArena = new Arena(combat.name(), combat.type(), combat.width(), combat.height(),
                    combat.platforms(), spawns, combat.npcSpawns(), combat.groundY(), combat.sidePassage());
            renderData(directory, "combat-" + names[i], poseArena);
        }
    }

    private static void renderData(Path directory, String name, Arena arena) throws Exception {
        GameSession session = new GameSession(UUID.randomUUID(), arena);
        for (int slot = 0; slot < 4; slot++) {
            session.addPlayer(new Player(new UUID(0, slot + 1), "Player " + (slot + 1)),
                    slot, CharacterType.values()[slot]);
        }
        session.advance(Map.of(), GameConstants.TICK_SECONDS);
        Files.writeString(directory.resolve(name + ".json"), new JsonMessageCodec().encode(SnapshotMapper.map(1, session)));
    }
}
