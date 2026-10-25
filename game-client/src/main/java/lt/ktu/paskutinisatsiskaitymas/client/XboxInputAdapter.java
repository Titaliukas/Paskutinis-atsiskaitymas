package lt.ktu.paskutinisatsiskaitymas.client;

import com.studiohartman.jamepad.ControllerManager;

/** Xbox controller: left stick moves, A jumps, X ranged, B melee. */
final class XboxInputAdapter extends GamepadInputAdapter {
    XboxInputAdapter(ControllerManager manager) {
        super(manager);
    }

    @Override
    boolean matches(String controllerName) {
        return nameContainsAny(controllerName, "xbox", "xinput");
    }

    @Override
    PlayerInput map(GamepadReading reading) {
        boolean buttonA = reading.bottom();
        boolean buttonB = reading.right();
        boolean buttonX = reading.left();
        return new PlayerInput(movesLeft(reading), movesRight(reading),
                buttonA, buttonX || reading.rightShoulder(), buttonB || reading.leftShoulder());
    }
}
