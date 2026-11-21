package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.*;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationVisitorTest {
    private final NotificationVisitor visitor = new NotificationVisitor();

    @Test
    void producesTheSameWireTextsAsBefore() {
        UUID id = UUID.randomUUID();
        assertEquals(new GameEventMessage("ITEM_COLLECTED", "Ann picked up SHIELD"),
                new ItemCollectedEvent(id, "Ann", ItemType.SHIELD).accept(visitor));
        assertEquals(new GameEventMessage("ITEM_SPAWNED", "SPEED_BOOST appeared"),
                new ItemSpawnedEvent(id, ItemType.SPEED_BOOST).accept(visitor));
        assertEquals(new GameEventMessage("PLAYER_JOINED", "Ann joined"),
                new PlayerJoinedEvent(id, "Ann").accept(visitor));
        assertEquals(new GameEventMessage("PLAYER_LEFT", "Ann left"),
                new PlayerLeftEvent(id, "Ann").accept(visitor));
    }
}