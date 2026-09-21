package lt.ktu.paskutinisatsiskaitymas.application;

/**
 * Single-thread owner of match updates. The server scheduler invokes one fixed-duration tick at a time.
 */
public interface GameLoop {
    void tick();
}
