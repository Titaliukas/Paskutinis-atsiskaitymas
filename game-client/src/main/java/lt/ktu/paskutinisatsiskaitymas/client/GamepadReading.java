package lt.ktu.paskutinisatsiskaitymas.client;

import com.studiohartman.jamepad.ControllerState;

/**
 * The controller fields the game uses, copied out of Jamepad's {@link ControllerState}. Face buttons are named by
 * position because SDL reports Xbox and PlayStation pads the same way (bottom = A / Cross, right = B / Circle,
 * left = X / Square, top = Y / Triangle). Mapping works on this record, so it is testable without native SDL.
 */
record GamepadReading(String name, float leftStickX, boolean bottom, boolean right, boolean left, boolean top,
        boolean leftShoulder, boolean rightShoulder) {

    static GamepadReading from(ControllerState state) {
        return new GamepadReading(state.controllerType, state.leftStickX, state.a, state.b, state.x, state.y,
                state.lb, state.rb);
    }
}
