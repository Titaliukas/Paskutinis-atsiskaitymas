package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

/** Deterministic demonstration of production session/controller behavior, without networking or sleeps. */
public final class NPCStrategyDemo {
    private NPCStrategyDemo() { }

    public static void main(String[] args) {
        GameSession session = GameSession.createDefault();
        session.removeNPC(2);
        session.addPlayer(new Player(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Demo player"), 0);
        EnumSet<NPCActivity> seen = EnumSet.noneOf(NPCActivity.class);
        NPCActivity previous = null;
        long lastAttempt = 0;
        for (int tick = 0; tick < 600; tick++) {
            NPCView view = session.npcs().getFirst();
            if (view.activity() != previous) {
                System.out.printf("tick=%d NPC=%d %s x=%.2f vx=%.2f target=%s stress=%.0f%n",
                        tick, view.state().id(), view.activity(), view.state().preciseX(),
                        view.state().velocityX(), view.state().targetId(), session.characters().getFirst().stressLevel());
                previous = view.activity();
                seen.add(previous);
            }
            if (view.state().attackAttemptSequence() != lastAttempt) {
                lastAttempt = view.state().attackAttemptSequence();
                seen.add(NPCActivity.ATTACK);
                System.out.printf("  ATTACK resolved within tick: accepted attempt #%d; player stress=%.0f; next=%s%n",
                        lastAttempt, session.characters().getFirst().stressLevel(), view.activity());
            }
            if (seen.size() == NPCActivity.values().length && view.activity() == NPCActivity.PATROL) {
                System.out.println("All four production strategies demonstrated on NPC 1; flee returned to patrol.");
                return;
            }
            session.advance(Map.of(), GameConstants.TICK_SECONDS);
        }
        throw new IllegalStateException("Demo did not observe all four strategies: " + seen);
    }
}
