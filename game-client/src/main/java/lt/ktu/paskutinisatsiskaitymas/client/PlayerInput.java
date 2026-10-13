package lt.ktu.paskutinisatsiskaitymas.client;

/** Held control state as the local player sees it. The server derives jump edges and motion. */
record PlayerInput(boolean left, boolean right, boolean jump) {
    static final PlayerInput NONE = new PlayerInput(false, false, false);
}
