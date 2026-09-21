package lt.ktu.paskutinisatsiskaitymas.server;

/** Deployment configuration. JVM -Dgame.server.port overrides SERVER_PORT, then defaults to 8080. */
public record ServerConfig(int port) {
    public ServerConfig {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Server port must be between 1 and 65535");
        }
    }

    public static ServerConfig fromEnvironment() {
        String value = System.getProperty("game.server.port",
                System.getenv().getOrDefault("SERVER_PORT", "8080"));
        try {
            return new ServerConfig(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Server port must be an integer", exception);
        }
    }
}
