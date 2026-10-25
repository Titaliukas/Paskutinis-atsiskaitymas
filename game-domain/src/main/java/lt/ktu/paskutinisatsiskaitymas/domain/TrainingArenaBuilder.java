package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Training recipe in world coordinates, measured from the unchanged 1672 x 941 background. */
public final class TrainingArenaBuilder implements IArenaBuilder {
    // Foreground grass below the stadium rail: imageY about 836 at 1672 x 941.
    public static final double GROUND_Y = 480;
    private double width;
    private double height;
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Position> playerSpawns = new ArrayList<>();
    private final List<NPCSpawn> npcSpawns = new ArrayList<>();

    @Override
    public IArenaBuilder reset() {
        width = 0;
        height = 0;
        platforms.clear();
        playerSpawns.clear();
        npcSpawns.clear();
        return this;
    }

    @Override
    public IArenaBuilder buildBounds() {
        width = GameConstants.ARENA_WIDTH;
        height = GameConstants.ARENA_HEIGHT;
        return this;
    }

    @Override
    public IArenaBuilder buildPlatforms() {
        platforms.clear();
        Platform ground = new Platform(UUID.randomUUID(), new Position(0, 0),
                GameConstants.ARENA_WIDTH, GameConstants.ARENA_HEIGHT - GROUND_Y);
        platforms.add(ground.copyAt(new Position(0, GROUND_Y)));
        return this;
    }

    @Override
    public IArenaBuilder buildPlayerSpawns() {
        playerSpawns.clear();
        for (double x : new double[]{180, 738, 60, 858}) {
            playerSpawns.add(new Position(x, GROUND_Y - GameConstants.PLAYER_HEIGHT));
        }
        return this;
    }

    @Override
    public IArenaBuilder buildNPCSpawns() {
        npcSpawns.clear();
        return this;
    }

    @Override
    public Arena build() {
        return new Arena("Training arena", ArenaType.TRAINING, width, height, platforms, playerSpawns,
                npcSpawns, GROUND_Y, Optional.empty());
    }
}
