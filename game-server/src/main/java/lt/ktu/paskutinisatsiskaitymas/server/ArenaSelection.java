package lt.ktu.paskutinisatsiskaitymas.server;

import lt.ktu.paskutinisatsiskaitymas.domain.Arena;
import lt.ktu.paskutinisatsiskaitymas.domain.ArenaDirector;
import lt.ktu.paskutinisatsiskaitymas.domain.CombatArenaBuilder;
import lt.ktu.paskutinisatsiskaitymas.domain.IArenaBuilder;
import lt.ktu.paskutinisatsiskaitymas.domain.TrainingArenaBuilder;

/** One startup choice per server. JVM property wins over environment, including invalid/empty values. */
final class ArenaSelection {
    private ArenaSelection() { }

    static Arena fromEnvironment() {
        return create(System.getProperty("game.arena"), System.getenv("GAME_ARENA"));
    }

    static Arena create(String property, String environment) {
        String value = property != null ? property : environment != null ? environment : "combat";
        IArenaBuilder builder = switch (value) {
            case "combat" -> new CombatArenaBuilder();
            case "training" -> new TrainingArenaBuilder();
            default -> throw new IllegalArgumentException(
                    "Invalid game.arena / GAME_ARENA '" + value + "': expected combat or training");
        };
        return new ArenaDirector().construct(builder);
    }
}
