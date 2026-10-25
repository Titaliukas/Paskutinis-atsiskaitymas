package lt.ktu.paskutinisatsiskaitymas.client;

import com.studiohartman.jamepad.ControllerManager;
import java.awt.Component;

/** Input devices offered in the connection form; the single place that picks the matching adapter. */
enum InputDevice {
    KEYBOARD("Keyboard", false),
    XBOX("Xbox controller", true),
    PLAYSTATION("PlayStation controller", true);

    private final String label;
    private final boolean gamepad;

    InputDevice(String label, boolean gamepad) {
        this.label = label;
        this.gamepad = gamepad;
    }

    boolean gamepad() {
        return gamepad;
    }

    /** Creates and starts the adapter. Gamepad devices need an initialised manager. */
    InputSource create(Component keyboardSource, ControllerManager manager) {
        return switch (this) {
            case KEYBOARD -> new KeyboardInputAdapter(keyboardSource);
            case XBOX -> started(new XboxInputAdapter(manager));
            case PLAYSTATION -> started(new PlayStationInputAdapter(manager));
        };
    }

    /** Button guide for the device, shown when it is selected. */
    String controlsHint() {
        return switch (this) {
            case KEYBOARD -> "Keyboard: A/D or arrows move, Space/W/Up jump, J ranged, K melee";
            case XBOX -> "Xbox: left stick move, A jump, X ranged, B melee";
            case PLAYSTATION -> "PlayStation: left stick move, Cross jump, Square ranged, Circle melee";
        };
    }

    private static InputSource started(GamepadInputAdapter adapter) {
        adapter.start();
        return adapter;
    }

    @Override
    public String toString() {
        return label;
    }
}
