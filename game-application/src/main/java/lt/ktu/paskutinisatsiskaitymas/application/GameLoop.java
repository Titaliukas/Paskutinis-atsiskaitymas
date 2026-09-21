package lt.ktu.paskutinisatsiskaitymas.application;

/**
 * Single-thread owner of future match updates. A later scheduler will invoke one tick at a time,
 * drain application commands first, and then advance rules. No scheduler or simulation exists yet.
 */
public interface GameLoop {
    void tick();
}
