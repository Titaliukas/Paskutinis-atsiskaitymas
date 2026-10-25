package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Explicit old prototype geometry for tests of existing behavior, independent of production defaults. */
final class TestArenas {
    private TestArenas() { }

    static Arena geometry(String name, double width, double height, List<Platform> platforms) {
        return new Arena(name, ArenaType.TRAINING, width, height, platforms,
                List.of(new Position(180, 416), new Position(738, 416),
                        new Position(60, 416), new Position(858, 416)),
                List.of(), 480, Optional.empty());
    }

    static GameSession prototypeSession() {
        Platform template = new Platform(UUID.randomUUID(), new Position(0, 0), 160, 20);
        Arena arena = geometry("Test prototype", 960, 540, List.of(
                template.copyAt(new Position(400, 280)), template.copyAt(new Position(140, 380)),
                template.copyAt(new Position(660, 380)),
                new Platform(UUID.randomUUID(), new Position(0, 480), 960, 60)));
        GameSession session = new GameSession(UUID.randomUUID(), arena);
        session.spawnNPC(1, new Position(360, 416), 300, 420);
        session.spawnNPC(2, new Position(580, 416), 530, 650);
        return session;
    }
}
