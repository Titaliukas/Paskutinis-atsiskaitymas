package lt.ktu.paskutinisatsiskaitymas.client;

/** A local action that can restore the state it changed. */
interface UndoableCommand {
    void execute();

    void undo();
}
