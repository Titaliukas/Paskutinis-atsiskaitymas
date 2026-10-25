package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;

/** Shares the construction order without knowing any concrete geometry or entity count. */
public final class ArenaDirector {
    public Arena construct(IArenaBuilder builder) {
        return Objects.requireNonNull(builder, "builder")
                .reset()
                .buildBounds()
                .buildPlatforms()
                .buildPlayerSpawns()
                .buildNPCSpawns()
                .build();
    }
}
