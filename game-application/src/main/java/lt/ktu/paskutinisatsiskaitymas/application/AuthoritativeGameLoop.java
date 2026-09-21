package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Objects;
import lt.ktu.paskutinisatsiskaitymas.domain.GameConstants;

/** Drains a bounded amount of work and advances the match exactly once per scheduled tick. */
public final class AuthoritativeGameLoop implements GameLoop {
    public static final int MAX_COMMANDS_PER_TICK = 128;
    private final CommandQueue<GameCommand> commands;
    private final MatchController match;
    private long tickNumber;

    public AuthoritativeGameLoop(CommandQueue<GameCommand> commands, MatchController match) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.match = Objects.requireNonNull(match, "match");
    }

    @Override
    public void tick() {
        for (int processed = 0; processed < MAX_COMMANDS_PER_TICK; processed++) {
            var command = commands.poll();
            if (command.isEmpty()) {
                break;
            }
            match.handle(command.orElseThrow());
        }
        match.advance(GameConstants.TICK_SECONDS);
        tickNumber++;
    }

    public long tickNumber() {
        return tickNumber;
    }
}
