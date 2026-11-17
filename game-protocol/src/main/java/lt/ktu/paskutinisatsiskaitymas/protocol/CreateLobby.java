package lt.ktu.paskutinisatsiskaitymas.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Client request to create a waiting lobby; nickname is not authentication.
 * The character is chosen by the player and may repeat across players. A missing value means PATASIJUS.
 */
public record CreateLobby(String nickname, String character, StatAllocation stats) implements Message {
    public static final String DEFAULT_CHARACTER = "PATASIJUS";
    public static final List<String> CHARACTERS = List.of("PATASIJUS", "VACIJUS", "JURGELINIS", "GOGIS");

    public CreateLobby(String nickname, String character, StatAllocation stats) {
        this.nickname = WireText.require(nickname, "nickname", 32);
        this.character = character == null ? DEFAULT_CHARACTER : character;
        if (!CHARACTERS.contains(this.character)) {
            throw new IllegalArgumentException("Unknown character");
        }
        this.stats = stats == null ? StatAllocation.DEFAULT : stats;
    }

    /** Optional CREATE_LOBBY fields have defaults without weakening required fields on other messages. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreateLobby fromJson(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException("CREATE_LOBBY must be an object");
        }
        json.fieldNames().forEachRemaining(field -> {
            if (!List.of("nickname", "character", "stats").contains(field)) {
                throw new IllegalArgumentException("Unknown CREATE_LOBBY field: " + field);
            }
        });
        JsonNode name = json.get("nickname");
        JsonNode character = json.get("character");
        if (name == null || !name.isTextual()
                || character != null && !character.isNull() && !character.isTextual()) {
            throw new IllegalArgumentException("Nickname and character must be text");
        }
        JsonNode stats = json.get("stats");
        return new CreateLobby(name.textValue(), character == null ? null : character.textValue(),
                stats == null ? StatAllocation.DEFAULT : StatAllocation.fromJson(stats));
    }

    public CreateLobby(String nickname, String character) {
        this(nickname, character, StatAllocation.DEFAULT);
    }

    public CreateLobby(String nickname) {
        this(nickname, DEFAULT_CHARACTER);
    }
}
