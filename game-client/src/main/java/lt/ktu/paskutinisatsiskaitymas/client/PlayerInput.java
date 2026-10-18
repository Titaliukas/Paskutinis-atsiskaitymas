package lt.ktu.paskutinisatsiskaitymas.client;

/** Complete keyboard state: movement keys plus the ranged (J) and melee (K) attack keys. */
public record PlayerInput(boolean left, boolean right, boolean jump, boolean ranged, boolean melee) {
    public static final PlayerInput NONE = new PlayerInput(false, false, false, false, false);
}