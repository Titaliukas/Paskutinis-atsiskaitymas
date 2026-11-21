package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.Objects;

/** Confirms the character and stats for the sending room member. */
public record SelectCharacter(String character, StatAllocation stats) implements Message {
    public static final String DEFAULT_CHARACTER = "PATASIJUS";
    public static final List<String> CHARACTERS = List.of("PATASIJUS", "VACIJUS", "JURGELINIS", "GOGIS");

    public SelectCharacter {
        if (character == null || !CHARACTERS.contains(character)) {
            throw new IllegalArgumentException("Unknown character");
        }
        Objects.requireNonNull(stats, "stats");
    }
}
