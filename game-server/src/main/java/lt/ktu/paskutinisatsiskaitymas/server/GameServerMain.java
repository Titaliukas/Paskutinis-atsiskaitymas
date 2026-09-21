package lt.ktu.paskutinisatsiskaitymas.server;

import java.net.InetSocketAddress;
import java.time.Duration;

/** Standalone server process; closing connections on SIGINT/SIGTERM leaves no simulation running. */
public final class GameServerMain {
    private GameServerMain() { }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.fromEnvironment();
        GameWebSocketServer server = new GameWebSocketServer(new InetSocketAddress("0.0.0.0", config.port()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.close();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "server-shutdown"));
        server.start();
        try {
            server.awaitStarted(Duration.ofSeconds(10));
        } catch (Exception exception) {
            server.close();
            throw exception;
        }
    }
}
