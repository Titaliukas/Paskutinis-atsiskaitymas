package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.Locale;

/** Shared transport formatting, not a game rule or authentication credential. */
public final class LobbyCode {
    private LobbyCode() { }

    public static String normalize(String code) {
        String normalized = WireText.require(code, "code", 32).trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{6}")) {
            throw new IllegalArgumentException("Lobby code must contain six letters or digits");
        }
        return normalized;
    }
}
