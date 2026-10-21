package lt.ktu.paskutinisatsiskaitymas.client;

import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;
import lt.ktu.paskutinisatsiskaitymas.protocol.StatAllocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterSelectionCommandTest {
    private final CharacterSelection selection = new CharacterSelection();
    private final CommandHistory history = new CommandHistory();

    @Test
    void mixedCommandsRestoreCharacterAndItsCustomizedStatsExactly() {
        history.execute(new ChangeStatCommand(selection, SelectableStat.HEALTH, 75));
        history.execute(new ChangeStatCommand(selection, SelectableStat.SPEED, 125));
        CharacterSelection.State custom = selection.snapshot();
        history.execute(new SelectCharacterCommand(selection, "GOGIS"));
        assertEquals(StatAllocation.DEFAULT, selection.stats());
        history.execute(new ChangeStatCommand(selection, SelectableStat.JUMP_HEIGHT, 50));
        history.undo();
        assertEquals("GOGIS", selection.selectedCharacter());
        assertEquals(StatAllocation.DEFAULT, selection.stats());
        history.undo();
        assertEquals(custom, selection.snapshot());
        history.undo();
        assertEquals(new StatAllocation(75, 100, 100), selection.stats());
        history.execute(new ChangeStatCommand(selection, SelectableStat.JUMP_HEIGHT, 125));
        history.undo();
        history.undo();
        assertEquals(StatAllocation.DEFAULT, selection.stats());
        assertEquals(Hello.DEFAULT_CHARACTER, selection.selectedCharacter());
        assertFalse(history.canUndo());
    }

    @Test
    void statCommandsCaptureAtExecutionAndClearRetainsCustomBaseline() {
        ChangeStatCommand command = new ChangeStatCommand(selection, SelectableStat.SPEED, 125);
        assertThrows(IllegalStateException.class, command::undo);
        selection.changeStat(SelectableStat.HEALTH, 75);
        CharacterSelection.State before = selection.snapshot();
        history.execute(command);
        assertThrows(IllegalStateException.class, command::execute);
        history.undo();
        assertEquals(before, selection.snapshot());
        history.execute(command);
        CharacterSelection.State baseline = selection.snapshot();
        history.clear();
        history.undo();
        assertEquals(baseline, selection.snapshot());
        history.execute(new SelectCharacterCommand(selection, "VACIJUS"));
        history.undo();
        assertEquals(baseline, selection.snapshot());
        assertFalse(history.canUndo());
    }

    @Test
    void invalidStatsCannotMutateReceiverOrEnterHistory() {
        CharacterSelection.State before = selection.snapshot();
        for (int value : new int[]{45, 51, 105, 155, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> history.execute(new ChangeStatCommand(selection, SelectableStat.HEALTH, value)));
            assertEquals(before, selection.snapshot());
            assertFalse(history.canUndo());
        }
    }

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
