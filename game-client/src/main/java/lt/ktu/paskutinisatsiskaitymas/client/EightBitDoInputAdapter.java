package lt.ktu.paskutinisatsiskaitymas.client;

import com.studiohartman.jamepad.ControllerManager;

/** 8BitDo controller using SDL's normalized stick and positional face buttons. */
final class EightBitDoInputAdapter extends GamepadInputAdapter {
    EightBitDoInputAdapter(ControllerManager manager) {
        super(manager);
    }

    @Override
    boolean matches(String controllerName) {
        return nameContainsAny(controllerName, "8bitdo");
    }

    @Override
    PlayerInput map(GamepadReading reading) {
        return new PlayerInput(movesLeft(reading), movesRight(reading),
                reading.bottom(), reading.left() || reading.rightShoulder(),
                reading.right() || reading.leftShoulder());
    }
}
