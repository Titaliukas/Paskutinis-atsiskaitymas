package lt.ktu.paskutinisatsiskaitymas.client;

import com.studiohartman.jamepad.ControllerManager;
import com.studiohartman.jamepad.ControllerState;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Timer;

/**
 * Adapts Jamepad's polling controller API (the adaptee) to {@link InputSource}. Jamepad must be asked for the
 * controller state; the game expects to be told when input changes. A Swing timer polls on the EDT and publishes
 * only real changes, like the keyboard adapter. Subclasses decide which controllers they accept and how buttons map.
 */
abstract class GamepadInputAdapter implements InputSource {
    /** Stick deflection needed before it counts as movement, so a resting stick does not drift. */
    static final double STICK_DEAD_ZONE = 0.35;
    private static final int POLL_MILLIS = 16;
    private static final int MAX_CONTROLLERS = 4;

    private final ControllerManager manager;
    private final Timer timer;
    private PlayerInput last = PlayerInput.NONE;
    private Consumer<PlayerInput> listener = input -> { };

    GamepadInputAdapter(ControllerManager manager) {
        this.manager = manager;
        this.timer = new Timer(POLL_MILLIS, event -> poll());
    }

    /** True if this adapter handles a controller reporting this name, for example "PS5 Controller". */
    abstract boolean matches(String controllerName);

    /** Translates one controller reading into the game's input state. */
    abstract PlayerInput map(GamepadReading reading);

    /** Starts polling. Separate from the constructor so tests can drive {@link #update} without native SDL. */
    final void start() {
        Objects.requireNonNull(manager, "manager");
        timer.start();
    }

    private void poll() {
        manager.update();
        update(firstMatchingController());
    }

    private GamepadReading firstMatchingController() {
        for (int index = 0; index < MAX_CONTROLLERS; index++) {
            ControllerState state = manager.getState(index);
            if (state.isConnected && matches(state.controllerType)) {
                return GamepadReading.from(state);
            }
        }
        return null;
    }

    /** Publishes the mapped reading if it differs from the last one; no matching controller means no input. */
    final void update(GamepadReading reading) {
        publishIfChanged(reading == null ? PlayerInput.NONE : map(reading));
    }

    static boolean movesLeft(GamepadReading reading) {
        return reading.leftStickX() < -STICK_DEAD_ZONE;
    }

    static boolean movesRight(GamepadReading reading) {
        return reading.leftStickX() > STICK_DEAD_ZONE;
    }

    static boolean nameContainsAny(String controllerName, String... fragments) {
        if (controllerName == null) {
            return false;
        }
        String name = controllerName.toLowerCase(Locale.ROOT);
        for (String fragment : fragments) {
            if (name.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PlayerInput currentInput() {
        return last;
    }

    @Override
    public void setListener(Consumer<PlayerInput> onChange) {
        listener = Objects.requireNonNull(onChange, "onChange");
    }

    @Override
    public void reset() {
        publishIfChanged(PlayerInput.NONE);
    }

    @Override
    public void close() {
        timer.stop();
    }

    private void publishIfChanged(PlayerInput current) {
        if (!current.equals(last)) {
            last = current;
            listener.accept(current);
        }
    }
}
