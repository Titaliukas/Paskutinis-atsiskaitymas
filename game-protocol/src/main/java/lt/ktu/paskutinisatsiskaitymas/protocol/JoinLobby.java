package lt.ktu.paskutinisatsiskaitymas.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record JoinLobby(String code, String nickname, String character, StatAllocation stats) implements Message {
    public JoinLobby {
        code = LobbyCode.normalize(code);
        CreateLobby selection = new CreateLobby(nickname, character, stats);
        nickname = selection.nickname();
        character = selection.character();
        stats = selection.stats();
    }

    public JoinLobby(String code, String nickname) {
        this(code, nickname, CreateLobby.DEFAULT_CHARACTER, StatAllocation.DEFAULT);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static JoinLobby fromJson(JsonNode json) {
        if (json == null || !json.isObject() || !json.hasNonNull("code") || !json.get("code").isTextual()) {
            throw new IllegalArgumentException("JOIN_LOBBY requires a text code");
        }
        ObjectNode selectionJson = json.deepCopy();
        String code = selectionJson.remove("code").textValue();
        CreateLobby selection = CreateLobby.fromJson(selectionJson);
        return new JoinLobby(code, selection.nickname(), selection.character(), selection.stats());
    }
}
