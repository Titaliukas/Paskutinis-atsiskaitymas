package lt.ktu.paskutinisatsiskaitymas.client;

import javax.swing.SwingUtilities;

/** Independent desktop process. Optional positional arguments prefill address and nickname. */
public final class GameClientMain {
    private GameClientMain() { }

    public static void main(String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: java -jar game-client.jar [server-address] [nickname]");
        }
        String address = args.length > 0 ? args[0] : "ws://localhost:8080/game";
        String nickname = args.length > 1 ? args[1] : "Player";
        SwingUtilities.invokeLater(() -> new ConnectionWindow(address, nickname).setVisible(true));
    }
}
