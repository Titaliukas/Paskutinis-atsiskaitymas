package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.Objects;

/** Remembers the choice at execution time so undo restores the previous character. */
final class SelectCharacterCommand implements UndoableCommand {
    private final CharacterSelection selection;
    private final String character;
    private String previousCharacter;
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
        String previous = selection.selectedCharacter();
        selection.select(character);
        previousCharacter = previous;
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new IllegalStateException("Command has not executed");
        }
        selection.select(previousCharacter);
        executed = false;
    }
}
