package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.List;
import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.domain.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExaminatorInputTest {
    @Test
    void allPosturesApplyBeforeAttacksAndVolleyRegardlessOfJoinOrderAndEdgesDoNotAutoRepeat() {
        for (boolean reverse : List.of(false, true)) {
            GameSession session = new GameSession(UUID.randomUUID(), new ArenaDirector().construct(new TrainingArenaBuilder()));
            MatchController controller = new MatchController(session);
            UUID owner = UUID.randomUUID();
            UUID guest = UUID.randomUUID();
            for (UUID id : reverse ? List.of(guest, owner) : List.of(owner, guest)) {
                controller.handle(new JoinPlayerCommand(id, id.equals(owner) ? 0 : 1, "Player", CharacterType.VACIJUS));
            }
            controller.handle(input(owner, 1, true, false, false));
            for (int i = 0; i < 125; i++) { controller.advance(GameConstants.TICK_SECONDS); }
            // On exactly the firing tick, guest requests prone and all attacks at once.
            controller.handle(input(guest, 1, true, true, true));
            controller.handle(input(guest, 0, false, false, false)); // stale must not stand the guest up
            controller.advance(GameConstants.TICK_SECONDS);
            var state = session.characters().stream().filter(c -> c.playerId().equals(guest)).findFirst().orElseThrow();
            assertTrue(state.prone());
            assertEquals(100, state.health());
            assertTrue(session.activeAttacks().isEmpty());
            var volley = session.examinators().stream().filter(e -> e.ownerId().equals(owner)).findFirst().orElseThrow();
            assertEquals(0, volley.shotSequence());
            assertTrue(volley.shots().isEmpty());
            for (int i = 0; i < 1800; i++) { controller.advance(GameConstants.TICK_SECONDS); }
            assertEquals(0, session.examinators().stream().filter(e -> e.ownerId().equals(owner)).findFirst().orElseThrow().shotSequence());
            assertEquals(0, session.examinators().stream().filter(e -> e.ownerId().equals(owner)).findFirst().orElseThrow().cooldownRemaining());
            controller.handle(input(owner, 2, false, false, false));
            controller.handle(input(owner, 3, true, false, false));
            for (int i = 0; i < 126; i++) { controller.advance(GameConstants.TICK_SECONDS); }
            assertEquals(0, session.examinators().stream().filter(e -> e.ownerId().equals(owner)).findFirst().orElseThrow().shotSequence());
            assertTrue(session.examinators().stream().filter(e -> e.ownerId().equals(owner)).findFirst().orElseThrow().cooldownRemaining() > 27);
            controller.handle(new LeavePlayerCommand(owner));
            assertEquals(1, session.examinators().size());
        }
    }

    private static InputCommand input(UUID id, long sequence, boolean examinator, boolean prone, boolean attacks) {
        return new InputCommand(id, sequence, attacks, attacks, attacks, attacks, attacks, examinator, prone);
    }
}
