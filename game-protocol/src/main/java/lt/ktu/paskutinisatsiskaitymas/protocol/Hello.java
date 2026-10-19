package lt.ktu.paskutinisatsiskaitymas.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Client request to occupy a game slot; nickname is not authentication.
 * The character is chosen by the player and may repeat across players. A missing value means MARTY.
 */
public record Hello(String nickname, String character) implements Message {
    public static final String DEFAULT_CHARACTER = "MARTY";
    public static final List<String> CHARACTERS = List.of("MARTY", "JUSTAS", "JURGIS", "GUOG");

    @JsonCreator
    public Hello(
            @JsonProperty("nickname") String nickname,
            @JsonProperty(value = "character", required = false) String character) {
        this.nickname = WireText.require(nickname, "nickname", 32);
        this.character = character == null ? DEFAULT_CHARACTER : character;
        if (!CHARACTERS.contains(this.character)) {
            throw new IllegalArgumentException("Unknown character");
        }
    }

    public Hello(String nickname) {
        this(nickname, DEFAULT_CHARACTER);
    }
}