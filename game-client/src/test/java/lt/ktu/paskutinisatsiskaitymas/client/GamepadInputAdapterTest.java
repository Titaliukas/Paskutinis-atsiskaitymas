package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the mapping without native SDL: adapters are never started, readings are fed directly. */
class GamepadInputAdapterTest {
    private final XboxInputAdapter xbox = new XboxInputAdapter(null);
    private final PlayStationInputAdapter playStation = new PlayStationInputAdapter(null);

    private final EightBitDoInputAdapter eightBitDo = new EightBitDoInputAdapter(null);

    private List<GamepadInputAdapter> adapters() {
        return List.of(xbox, playStation, eightBitDo);
    }

    private static GamepadReading idle(String name) {
        return new GamepadReading(name, 0f, false, false, false, false, false, false);
    }

    private static GamepadReading stick(float x) {
        return new GamepadReading("Xbox Series X Controller", x, false, false, false, false,
                false, false);
    }

    /** Face buttons by position: bottom, right, left, top. */
    private static GamepadReading face(boolean bottom, boolean right, boolean left, boolean top) {
        return new GamepadReading("PS5 Controller", 0f, bottom, right, left, top, false, false);
    }

    private static PlayerInput state(boolean left, boolean right, boolean jump, boolean ranged, boolean melee) {
        return new PlayerInput(left, right, jump, ranged, melee);
    }

    @Test
    void eachAdapterAcceptsOnlyItsOwnControllers() {
        for (String name : List.of("Xbox 360 Controller", "Xbox Series X Controller", "XInput Controller")) {
            assertTrue(xbox.matches(name), name);
            assertFalse(playStation.matches(name), name);
            assertFalse(eightBitDo.matches(name), name);
        }
        for (String name : List.of("PS4 Controller", "PS5 Controller", "DualSense Wireless Controller",
                "Sony Interactive Entertainment Wireless Controller")) {
            assertTrue(playStation.matches(name), name);
            assertFalse(xbox.matches(name), name);
            assertFalse(eightBitDo.matches(name), name);
        }
        for (String name : List.of("8BitDo Ultimate 2C Wireless", "8bitdo", "8BITDO Controller")) {
            assertTrue(eightBitDo.matches(name), name);
            assertFalse(xbox.matches(name), name);
            assertFalse(playStation.matches(name), name);
        }
        for (String name : List.of("", "Unknown Controller", "Nintendo Switch Pro Controller", "8Bit Controller")) {
            assertFalse(eightBitDo.matches(name), name);
        }
        assertFalse(eightBitDo.matches(null));
        assertFalse(xbox.matches(null));
        assertFalse(playStation.matches("Nintendo Switch Pro Controller"));
    }

    @Test
    void faceButtonsMapToJumpRangedAndMelee() {
        for (GamepadInputAdapter adapter : adapters()) {
            assertEquals(state(false, false, true, false, false), adapter.map(face(true, false, false, false)));
            assertEquals(state(false, false, false, true, false), adapter.map(face(false, false, true, false)));
            assertEquals(state(false, false, false, false, true), adapter.map(face(false, true, false, false)));
            assertEquals(PlayerInput.NONE, adapter.map(face(false, false, false, true)), "top button is unused");
        }
    }

    @Test
    void shouldersAreAlternativeAttackButtons() {
        GamepadReading shoulders = new GamepadReading("Xbox One Controller", 0f, false, false, false, false,
                true, true);
        for (GamepadInputAdapter adapter : adapters()) {
            assertEquals(state(false, false, false, true, true), adapter.map(shoulders));
            assertEquals(state(false, false, false, true, false), adapter.map(
                    new GamepadReading("", 0f, false, false, false, false, false, true)));
            assertEquals(state(false, false, false, false, true), adapter.map(
                    new GamepadReading("", 0f, false, false, false, false, true, false)));
        }
    }

    @Test
    void onlyTheLeftStickMovesTheCharacter() {
        // Movement reads only leftStickX; face and shoulder buttons never produce left or right.
        GamepadReading everythingButStick = new GamepadReading("Xbox One Controller", 0f, true, true, true, true,
                true, true);
        for (GamepadInputAdapter adapter : adapters()) {
            PlayerInput input = adapter.map(everythingButStick);
            assertFalse(input.left());
            assertFalse(input.right());
        }
    }

    @Test
    void stickNeedsToPassTheDeadZone() {
        for (GamepadInputAdapter adapter : adapters()) {
            assertEquals(PlayerInput.NONE, adapter.map(stick(-0.2f)));
            assertEquals(PlayerInput.NONE, adapter.map(stick(0.3f)));
            assertEquals(state(true, false, false, false, false), adapter.map(stick(-0.8f)));
            assertEquals(state(false, true, false, false, false), adapter.map(stick(0.8f)));
        }
    }

    @Test
    void publishesOnlyChangesAndTreatsMissingControllerAsNoInput() {
        for (GamepadInputAdapter adapter : adapters()) {
            List<PlayerInput> published = new ArrayList<>();
            InputSource input = adapter;
            input.setListener(published::add);

            adapter.update(stick(0.9f));
            adapter.update(stick(0.95f));
            adapter.update(idle("Xbox Series X Controller"));
            adapter.update(stick(-0.9f));
            adapter.update(null);

            assertEquals(List.of(state(false, true, false, false, false), PlayerInput.NONE,
                    state(true, false, false, false, false), PlayerInput.NONE), published);
            assertEquals(PlayerInput.NONE, input.currentInput());
        }
    }

    @Test
    void resetReleasesHeldInputOnce() {
        for (GamepadInputAdapter adapter : adapters()) {
            List<PlayerInput> published = new ArrayList<>();
            InputSource input = adapter;
            input.setListener(published::add);
            adapter.update(face(true, false, false, false));
            input.reset();
            input.reset();
            assertEquals(List.of(state(false, false, true, false, false), PlayerInput.NONE), published);
        }
    }

    @Test
    void closeWithoutStartIsSafeAndStartNeedsAManager() {
        for (GamepadInputAdapter adapter : adapters()) {
            assertDoesNotThrow(adapter::close);
            assertThrows(NullPointerException.class, adapter::start);
        }
    }

    @Test
    void everyDeviceHasALabelAndHint() {
        for (InputDevice device : InputDevice.values()) {
            assertFalse(device.toString().isBlank(), device.name());
            assertFalse(device.controlsHint().isBlank(), device.name());
        }
        assertFalse(InputDevice.KEYBOARD.gamepad());
        assertTrue(InputDevice.XBOX.gamepad());
        assertTrue(InputDevice.PLAYSTATION.gamepad());
        assertTrue(InputDevice.EIGHT_BIT_DO.gamepad());
        assertEquals("8BitDo controller", InputDevice.EIGHT_BIT_DO.toString());
        assertEquals("8BitDo: left stick move, bottom face button jump, "
                + "left face button/right shoulder ranged, right face button/left shoulder melee",
                InputDevice.EIGHT_BIT_DO.controlsHint());
    }
}
