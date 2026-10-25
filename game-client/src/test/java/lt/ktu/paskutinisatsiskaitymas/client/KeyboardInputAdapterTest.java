package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyboardInputAdapterTest {
    private final JPanel source = new JPanel();
    private final KeyboardInputAdapter adapter = new KeyboardInputAdapter(source);
    private final InputSource input = adapter;
    private final List<PlayerInput> published = new ArrayList<>();

    KeyboardInputAdapterTest() {
        input.setListener(published::add);
    }

    private static PlayerInput state(boolean left, boolean right, boolean jump, boolean ranged, boolean melee) {
        return new PlayerInput(left, right, jump, ranged, melee);
    }

    private KeyEvent key(int id, int keyCode) {
        return new KeyEvent(source, id, 0, 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private void press(int keyCode) {
        adapter.keyPressed(key(KeyEvent.KEY_PRESSED, keyCode));
    }

    private void release(int keyCode) {
        adapter.keyReleased(key(KeyEvent.KEY_RELEASED, keyCode));
    }

    @Test
    void translatesBoundKeysIntoHeldInputState() {
        press(KeyEvent.VK_A);
        assertEquals(state(true, false, false, false, false), input.currentInput());
        release(KeyEvent.VK_A);
        press(KeyEvent.VK_D);
        assertEquals(state(false, true, false, false, false), input.currentInput());
        press(KeyEvent.VK_SPACE);
        assertEquals(state(false, true, true, false, false), input.currentInput());
        assertEquals(List.of(state(true, false, false, false, false), PlayerInput.NONE,
                state(false, true, false, false, false), state(false, true, true, false, false)), published);
    }

    @Test
    void translatesAttackKeysIntoInputState() {
        press(KeyEvent.VK_J);
        assertEquals(state(false, false, false, true, false), input.currentInput());
        press(KeyEvent.VK_K);
        assertEquals(state(false, false, false, true, true), input.currentInput());
        release(KeyEvent.VK_J);
        release(KeyEvent.VK_K);
        assertEquals(PlayerInput.NONE, input.currentInput());
        assertEquals(List.of(
                state(false, false, false, true, false),
                state(false, false, false, true, true),
                state(false, false, false, false, true),
                PlayerInput.NONE), published);
    }

    @Test
    void keyRepeatAndAlternateJumpKeysDoNotRepublish() {
        press(KeyEvent.VK_W);
        press(KeyEvent.VK_W);
        press(KeyEvent.VK_SPACE);
        release(KeyEvent.VK_W);
        assertEquals(List.of(state(false, false, true, false, false)), published);
        release(KeyEvent.VK_SPACE);
        assertEquals(PlayerInput.NONE, input.currentInput());
    }

    @Test
    void attackKeyRepeatDoesNotRepublish() {
        press(KeyEvent.VK_J);
        press(KeyEvent.VK_J);
        assertEquals(List.of(state(false, false, false, true, false)), published);
    }

    @Test
    void ignoresUnboundKeys() {
        press(KeyEvent.VK_Q);
        release(KeyEvent.VK_Q);
        assertTrue(published.isEmpty());
        assertEquals(PlayerInput.NONE, input.currentInput());
    }

    @Test
    void resetAndFocusLossReleaseHeldKeys() {
        press(KeyEvent.VK_D);
        input.reset();
        input.reset();
        assertEquals(List.of(state(false, true, false, false, false), PlayerInput.NONE), published);

        press(KeyEvent.VK_A);
        for (FocusListener listener : source.getFocusListeners()) {
            listener.focusLost(new FocusEvent(source, FocusEvent.FOCUS_LOST));
        }
        assertEquals(PlayerInput.NONE, input.currentInput());

        press(KeyEvent.VK_K);
        for (FocusListener listener : source.getFocusListeners()) {
            listener.focusLost(new FocusEvent(source, FocusEvent.FOCUS_LOST));
        }
        assertEquals(PlayerInput.NONE, input.currentInput());
    }

    @Test
    void registersItselfOnTheSourceAndRejectsNullListener() {
        assertTrue(List.of(source.getKeyListeners()).contains(adapter));
        assertThrows(NullPointerException.class, () -> input.setListener(null));
    }

    @Test
    void closeDetachesFromTheSource() {
        int focusListeners = source.getFocusListeners().length;
        input.close();
        assertFalse(List.of(source.getKeyListeners()).contains(adapter));
        assertEquals(focusListeners - 1, source.getFocusListeners().length);
    }

    @Test
    void createsKeyboardAdapterForKeyboardDevice() {
        InputSource created = InputDevice.KEYBOARD.create(source, null);
        assertInstanceOf(KeyboardInputAdapter.class, created);
        created.close();
    }
}