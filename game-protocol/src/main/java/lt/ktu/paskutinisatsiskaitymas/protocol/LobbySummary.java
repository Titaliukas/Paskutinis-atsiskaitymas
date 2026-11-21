package lt.ktu.paskutinisatsiskaitymas.protocol;

public record LobbySummary(String code, String hostNickname, int playerCount) {
    public LobbySummary {
        code = LobbyCode.normalize(code);
        hostNickname = WireText.require(hostNickname, "hostNickname", 32);
        if (playerCount < 1 || playerCount >= 4) {
            throw new IllegalArgumentException("An open room must have one to three players");
        }
    }
}
