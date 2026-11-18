package lt.ktu.paskutinisatsiskaitymas.client;

/** Complete device intent: movement, ordinary attacks, Examinator activation and held prone. */
public record PlayerInput(boolean left, boolean right, boolean jump, boolean ranged, boolean melee,
        boolean examinator, boolean prone) {
    public PlayerInput(boolean left, boolean right, boolean jump, boolean ranged, boolean melee) {
        this(left, right, jump, ranged, melee, false, false);
    }

    public static final PlayerInput NONE = new PlayerInput(false, false, false, false, false);
}