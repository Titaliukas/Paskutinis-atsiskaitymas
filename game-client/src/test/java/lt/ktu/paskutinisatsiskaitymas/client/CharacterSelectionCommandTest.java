package lt.ktu.paskutinisatsiskaitymas.client;

import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterSelectionCommandTest {
    private final CharacterSelection selection = new CharacterSelection();
    private final CommandHistory history = new CommandHistory();

    @Test
    void commandExecutesAndUndoesThroughTheInterface() {
        UndoableCommand command = new SelectCharacterCommand(selection, "VACIJUS");
        assertEquals(Hello.DEFAULT_CHARACTER, selection.selectedCharacter());
        command.execute();
        assertEquals("VACIJUS", selection.selectedCharacter());
        command.undo();
        assertEquals(Hello.DEFAULT_CHARACTER, selection.selectedCharacter());
    }

    @Test
    void previousChoiceIsCapturedWhenExecutedRatherThanConstructed() {
        UndoableCommand command = new SelectCharacterCommand(selection, "GOGIS");
        selection.select("JURGELINIS");
        command.execute();
        command.undo();
        assertEquals("JURGELINIS", selection.selectedCharacter());
    }

    @Test
    void multipleChangesUndoInReverseOrderBackToTheDefault() {
        history.execute(new SelectCharacterCommand(selection, "VACIJUS"));
        history.execute(new SelectCharacterCommand(selection, "JURGELINIS"));
        history.execute(new SelectCharacterCommand(selection, "GOGIS"));
        assertTrue(history.canUndo());
        history.undo();
        assertEquals("JURGELINIS", selection.selectedCharacter());
        history.undo();
        assertEquals("VACIJUS", selection.selectedCharacter());
        history.undo();
        assertEquals(Hello.DEFAULT_CHARACTER, selection.selectedCharacter());
        assertFalse(history.canUndo());
    }

    @Test
    void emptyHistoryUndoDoesNothing() {
        assertDoesNotThrow(history::undo);
        assertFalse(history.canUndo());
        assertEquals(Hello.DEFAULT_CHARACTER, selection.selectedCharacter());
    }

    @Test
    void clearingHistoryRetainsTheChoiceAsTheNewBaseline() {
        history.execute(new SelectCharacterCommand(selection, "VACIJUS"));
        history.clear();
        history.undo();
        assertEquals("VACIJUS", selection.selectedCharacter());
        assertFalse(history.canUndo());
        history.execute(new SelectCharacterCommand(selection, "GOGIS"));
        history.undo();
        assertEquals("VACIJUS", selection.selectedCharacter());
        assertFalse(history.canUndo());
    }

    @Test
    void newSelectionAfterUndoKeepsOnlyTheRemainingHistory() {
        history.execute(new SelectCharacterCommand(selection, "VACIJUS"));
        history.execute(new SelectCharacterCommand(selection, "GOGIS"));
        history.undo();
        history.execute(new SelectCharacterCommand(selection, "JURGELINIS"));
        history.undo();
        assertEquals("VACIJUS", selection.selectedCharacter());
        history.undo();
        assertEquals(Hello.DEFAULT_CHARACTER, selection.selectedCharacter());
        assertFalse(history.canUndo());
    }

    @Test
    void invalidSelectionsDoNotChangeTheReceiverOrEnterHistory() {
        history.execute(new SelectCharacterCommand(selection, "VACIJUS"));
        assertThrows(IllegalArgumentException.class,
                () -> history.execute(new SelectCharacterCommand(selection, "UNKNOWN")));
        assertThrows(IllegalArgumentException.class,
                () -> history.execute(new SelectCharacterCommand(selection, null)));
        assertEquals("VACIJUS", selection.selectedCharacter());
        history.undo();
        assertEquals(Hello.DEFAULT_CHARACTER, selection.selectedCharacter());
        assertFalse(history.canUndo());
    }

    @Test
    void reexecutingAnActiveCommandCannotOverwriteItsUndoState() {
        UndoableCommand command = new SelectCharacterCommand(selection, "VACIJUS");
        assertThrows(IllegalStateException.class, command::undo);
        history.execute(command);
        assertThrows(IllegalStateException.class, () -> history.execute(command));
        history.undo();
        assertEquals(Hello.DEFAULT_CHARACTER, selection.selectedCharacter());
        assertFalse(history.canUndo());
    }
}
