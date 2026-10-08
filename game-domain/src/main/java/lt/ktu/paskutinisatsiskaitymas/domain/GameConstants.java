package lt.ktu.paskutinisatsiskaitymas.domain;

/** Tunable constants for the first authoritative platform prototype, expressed in world units. */
public final class GameConstants {
    public static final int MAX_PLAYERS = 4;
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
    public static final double ITEM_DURATION_SECONDS = 8.0;
    public static final double SPEED_BOOST_MULTIPLIER = 1.6;
    public static final double JUMP_BOOST_MULTIPLIER = 2.0;
    public static final double ITEM_RADIUS = 16.0;
    public static final double ITEM_SPAWN_INTERVAL_SECONDS = 6.0;
    public static final int ITEM_MAX_ACTIVE = 3;
    public static final double NPC_SPEED = 95.0;
    public static final double NPC_CHASE_MULTIPLIER = 1.5;
    public static final double NPC_FLEE_MULTIPLIER = 1.8;
    public static final double NPC_ATTACK_RANGE = 18.0;
    public static final double NPC_ATTACK_DAMAGE = 10.0;
    public static final double NPC_ATTACK_COOLDOWN_SECONDS = 1.2;
    public static final double NPC_DETECTION_RADIUS = 200.0;
    public static final double NPC_DETECTION_SECONDS = 0.6;
    public static final double NPC_SAFE_DISTANCE = 170.0;
    public static final double NPC_MAX_CHASE_SECONDS = 4.0;
    public static final double NPC_MAX_FLEE_SECONDS = 1.5;
    private GameConstants() { }

    public static double spawnX(int slot) {
        return switch (slot) {
            case 0 -> FIRST_SPAWN_X;
            case 1 -> SECOND_SPAWN_X;
            case 2 -> 60.0;
            case 3 -> 858.0;
            default -> throw new IllegalArgumentException("Player slot must be between 0 and 3");
        };
    }
}
