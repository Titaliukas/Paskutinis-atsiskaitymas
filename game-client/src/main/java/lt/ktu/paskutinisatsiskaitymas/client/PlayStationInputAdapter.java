package lt.ktu.paskutinisatsiskaitymas.client;

import com.studiohartman.jamepad.ControllerManager;

/** PlayStation controller: left stick moves, Cross jumps, Square ranged, Circle melee. */
final class PlayStationInputAdapter extends GamepadInputAdapter {
    PlayStationInputAdapter(ControllerManager manager) {
        super(manager);
    }

    @Override
    boolean matches(String controllerName) {
        return nameContainsAny(controllerName, "ps3", "ps4", "ps5", "playstation", "dualshock", "dualsense", "sony");
    }

    @Override
    PlayerInput map(GamepadReading reading) {
        boolean cross = reading.bottom();
        boolean circle = reading.right();
        boolean square = reading.left();
        return new PlayerInput(movesLeft(reading), movesRight(reading),
                cross, square || reading.rightShoulder(), circle || reading.leftShoulder());
    }
}
