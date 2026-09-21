package lt.ktu.paskutinisatsiskaitymas.domain;

/** Tunable constants for the first authoritative platform prototype, expressed in world units. */
public final class GameConstants {
    public static final int MAX_PLAYERS = 2;
    public static final int TICK_RATE = 60;
    public static final double TICK_SECONDS = 1.0 / TICK_RATE;
    public static final double ARENA_WIDTH = 960.0;
    public static final double ARENA_HEIGHT = 540.0;
    public static final double GROUND_X = 0.0;
    public static final double GROUND_Y = 480.0;
    public static final double GROUND_WIDTH = ARENA_WIDTH;
    public static final double GROUND_HEIGHT = ARENA_HEIGHT - GROUND_Y;
    public static final double PLAYER_WIDTH = 42.0;
    public static final double PLAYER_HEIGHT = 64.0;
    public static final double MOVE_SPEED = 260.0;
    public static final double JUMP_SPEED = 600.0;
    public static final double GRAVITY = 1_500.0;
    public static final double FIRST_SPAWN_X = 180.0;
    public static final double SECOND_SPAWN_X = 738.0;

    private GameConstants() { }

    public static double spawnX(int slot) {
        return switch (slot) {
            case 0 -> FIRST_SPAWN_X;
            case 1 -> SECOND_SPAWN_X;
            default -> throw new IllegalArgumentException("Player slot must be 0 or 1");
        };
    }
}
