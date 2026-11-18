package lt.ktu.paskutinisatsiskaitymas.domain;

/** One recorded shot, retained only for its remaining simulation-time feedback window. */
public record ExaminatorShot(long sequence, Position origin, Position endpoint, double remainingSeconds) { }
