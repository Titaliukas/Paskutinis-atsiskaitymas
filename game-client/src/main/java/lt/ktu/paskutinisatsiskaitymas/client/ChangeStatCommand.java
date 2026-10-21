package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.Objects;

/** One completed slider adjustment, restoring its exact prior selection on undo. */
final class ChangeStatCommand implements UndoableCommand {
    private final CharacterSelection selection;
    private final SelectableStat stat;
    private final int value;
    private CharacterSelection.State previous;
    private boolean executed;

    ChangeStatCommand(CharacterSelection selection, SelectableStat stat, int value) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.stat = Objects.requireNonNull(stat, "stat");
        this.value = value;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new IllegalStateException("Command has already executed");
        }
        CharacterSelection.State before = selection.snapshot();
        selection.changeStat(stat, value);
        previous = before;
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new IllegalStateException("Command has not executed");
        }
        selection.restore(previous);
        executed = false;
    }
}
