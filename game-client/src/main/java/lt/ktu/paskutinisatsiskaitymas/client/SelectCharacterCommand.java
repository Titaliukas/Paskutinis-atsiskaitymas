package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.Objects;

/** Remembers the choice at execution time so undo restores the previous character. */
final class SelectCharacterCommand implements UndoableCommand {
    private final CharacterSelection selection;
    private final String character;
    private CharacterSelection.State previous;
    private boolean executed;

    SelectCharacterCommand(CharacterSelection selection, String character) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.character = character;
    }

    @Override
    public void execute() {
        if (executed) {
            throw new IllegalStateException("Command has already executed");
        }
        CharacterSelection.State before = selection.snapshot();
        selection.select(character);
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
