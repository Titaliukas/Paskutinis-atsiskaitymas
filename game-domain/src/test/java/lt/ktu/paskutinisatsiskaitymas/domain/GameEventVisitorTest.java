package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameEventVisitorTest {
    private final GameEventVisitor<String> names = new GameEventVisitor<>() {
        public String visit(ItemSpawnedEvent e) { return "spawned"; }
        public String visit(ItemCollectedEvent e) { return "collected"; }
        public String visit(PlayerJoinedEvent e) { return "joined"; }
        public String visit(PlayerLeftEvent e) { return "left"; }
    };

    @Test
    void everyEventDispatchesToItsOwnVisitMethod() {
        UUID id = UUID.randomUUID();
        List<GameEvent> events = List.of(
                new ItemSpawnedEvent(id, ItemType.SHIELD),
                new ItemCollectedEvent(id, "A", ItemType.SHIELD),
                new PlayerJoinedEvent(id, "A"),
                new PlayerLeftEvent(id, "A"));
        assertEquals(List.of("spawned", "collected", "joined", "left"),
                events.stream().map(event -> event.accept(names)).toList());
    }
}