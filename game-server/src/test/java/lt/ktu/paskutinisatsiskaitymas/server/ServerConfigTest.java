package lt.ktu.paskutinisatsiskaitymas.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {
    @Test
    void propertyOverridesEnvironmentAndRejectsInvalidPorts() {
        String previous = System.getProperty("game.server.port");
        try {
            System.setProperty("game.server.port", "9090");
            assertEquals(9090, ServerConfig.fromEnvironment().port());
            for (String value : new String[]{"0", "65536", "-1", "not-a-port"}) {
                System.setProperty("game.server.port", value);
                assertThrows(IllegalArgumentException.class, ServerConfig::fromEnvironment);
            }
        } finally {
            if (previous == null) {
                System.clearProperty("game.server.port");
            } else {
                System.setProperty("game.server.port", previous);
            }
        }
    }
}
