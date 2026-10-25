package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Combat recipe in world coordinates, measured from the unchanged 1672 x 941 background. */
public final class CombatArenaBuilder implements IArenaBuilder {
    public static final double ROOF_X = 52;
    public static final double ROOF_Y = 222;
    public static final double ROOF_WIDTH = 857;
    public static final double CANOPY_X = 339;
    public static final double CANOPY_Y = 309;
    public static final double CANOPY_WIDTH = 281;
    public static final double LEFT_LEDGE_X = 0;
    public static final double RIGHT_LEDGE_X = 785;
    public static final double LEDGE_Y = 351;
    public static final double LEFT_LEDGE_WIDTH = 183;
    public static final double RIGHT_LEDGE_WIDTH = 175;
    public static final double GROUND_Y = 467;
    public static final double SURFACE_THICKNESS = 6;
    public static final double OPENING_TOP = 275;
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
        Platform roof = new Platform(UUID.randomUUID(), new Position(0, 0), ROOF_WIDTH, SURFACE_THICKNESS);
        platforms.add(roof.copyAt(new Position(ROOF_X, ROOF_Y)));
        Platform canopy = new Platform(UUID.randomUUID(), new Position(0, 0), CANOPY_WIDTH, SURFACE_THICKNESS);
        platforms.add(canopy.copyAt(new Position(CANOPY_X, CANOPY_Y)));
        Platform ledge = new Platform(UUID.randomUUID(), new Position(0, 0), LEFT_LEDGE_WIDTH, SURFACE_THICKNESS);
        platforms.add(ledge.copyAt(new Position(LEFT_LEDGE_X, LEDGE_Y)));
        Platform rightLedge = new Platform(UUID.randomUUID(), new Position(0, 0), RIGHT_LEDGE_WIDTH, SURFACE_THICKNESS);
        platforms.add(rightLedge.copyAt(new Position(RIGHT_LEDGE_X, LEDGE_Y)));
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
        npcSpawns.add(new NPCSpawn(1, new Position(360, ROOF_Y - GameConstants.PLAYER_HEIGHT),
                ROOF_X, ROOF_X + ROOF_WIDTH - GameConstants.PLAYER_WIDTH));
        npcSpawns.add(new NPCSpawn(2, new Position(580, GROUND_Y - GameConstants.PLAYER_HEIGHT), 530, 650));
        return this;
    }

    @Override
    public Arena build() {
        return new Arena("Combat arena", ArenaType.COMBAT, width, height, platforms, playerSpawns,
                npcSpawns, GROUND_Y, Optional.of(new SidePassage(OPENING_TOP, LEDGE_Y)));
    }
}
