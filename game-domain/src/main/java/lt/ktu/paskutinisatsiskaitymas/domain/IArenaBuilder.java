package lt.ktu.paskutinisatsiskaitymas.domain;

/** Builds configuration only; live entities belong to GameSession. */
public interface IArenaBuilder {
    IArenaBuilder reset();
    IArenaBuilder buildBounds();
    IArenaBuilder buildPlatforms();
    IArenaBuilder buildPlayerSpawns();
    IArenaBuilder buildNPCSpawns();
    Arena build();
}
