package lt.ktu.paskutinisatsiskaitymas.protocol;

/** Requests open rooms after a code, or the first page when the cursor is null. */
public record ListLobbies(String afterCode) implements Message {
    public ListLobbies {
        if (afterCode != null) {
            afterCode = LobbyCode.normalize(afterCode);
        }
    }
}
