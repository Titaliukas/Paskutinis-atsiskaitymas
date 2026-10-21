package lt.ktu.paskutinisatsiskaitymas.client;

import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import lt.ktu.paskutinisatsiskaitymas.protocol.StatAllocation;
import java.util.Objects;

/** Command receiver for the local character choice before joining a server. */
final class CharacterSelection {
    private String selectedCharacter = Hello.DEFAULT_CHARACTER;
    private StatAllocation stats = StatAllocation.DEFAULT;

    record State(String character, StatAllocation stats) {
        State {
            validateCharacter(character);
            validateStats(stats);
        }
    }

    String selectedCharacter() {
        return selectedCharacter;
    }

    void select(String character) {
        validateCharacter(character);
        selectedCharacter = character;
        stats = StatAllocation.DEFAULT;
    }

    StatAllocation stats() {
        return stats;
    }

    State snapshot() {
        return new State(selectedCharacter, stats);
    }

    void restore(State state) {
        Objects.requireNonNull(state, "state");
        selectedCharacter = state.character();
        stats = state.stats();
    }

    void changeStat(SelectableStat stat, int value) {
        StatAllocation changed = Objects.requireNonNull(stat, "stat").withValue(stats, value);
        validateStats(changed);
        stats = changed;
    }

    static void validateStats(StatAllocation stats) {
        Objects.requireNonNull(stats, "stats");
        for (SelectableStat stat : SelectableStat.values()) {
            int value = stat.value(stats);
            if (value < 50 || value > 150 || value % 5 != 0) {
                throw new IllegalArgumentException("Each stat must be 50–150 in steps of 5");
            }
        }
        if (remainingPoints(stats) < 0) {
            throw new IllegalArgumentException("Stats exceed the 300-point budget");
        }
    }

    static int remainingPoints(StatAllocation stats) {
        return 300 - stats.healthPercent() - stats.speedPercent() - stats.jumpHeightPercent();
    }

    private static void validateCharacter(String character) {
        if (character == null || !Hello.CHARACTERS.contains(character)) {
            throw new IllegalArgumentException("Unknown character");
        }
    }
}
