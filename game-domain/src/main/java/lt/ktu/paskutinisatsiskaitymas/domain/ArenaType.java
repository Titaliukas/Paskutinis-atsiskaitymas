package lt.ktu.paskutinisatsiskaitymas.domain;

/** Stable domain identity, independent of display names and resource paths. */
public enum ArenaType {
    COMBAT("combat"), TRAINING("training");

    private final String id;

    ArenaType(String id) { this.id = id; }
    public String id() { return id; }
}
