package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** EDT-owned invoker retaining successfully executed commands in undo order. */
final class CommandHistory {
    private final Deque<UndoableCommand> commands = new ArrayDeque<>();

    void execute(UndoableCommand command) {
        Objects.requireNonNull(command, "command").execute();
        commands.push(command);
    }

    void undo() {
        if (canUndo()) {
            commands.peek().undo();
            commands.pop();
        }
    }

    boolean canUndo() {
        return !commands.isEmpty();
    }

    void clear() {
        commands.clear();
    }
}
