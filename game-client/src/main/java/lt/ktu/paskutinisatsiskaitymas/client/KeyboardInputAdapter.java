package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Adapts Swing press/release transitions to complete input state without relying on key-repeat timing. */
final class KeyboardInputAdapter implements InputSource, KeyListener {
    private final Set<Integer> pressed = new HashSet<>();
    private final Component source;
    private final FocusListener focusReset = new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent event) {
            reset();
        }
    };
    private PlayerInput last = PlayerInput.NONE;
    private Consumer<PlayerInput> listener = input -> { };

    KeyboardInputAdapter(Component source) {
        this.source = Objects.requireNonNull(source, "source");
        source.addKeyListener(this);
        source.addFocusListener(focusReset);
    }

    @Override
    public void keyPressed(KeyEvent event) {
        if (isControl(event.getKeyCode()) && pressed.add(event.getKeyCode())) {
            publishIfChanged();
        }
    }

    @Override
    public void keyReleased(KeyEvent event) {
        if (pressed.remove(event.getKeyCode())) {
            publishIfChanged();
        }
    }

    @Override
    public void keyTyped(KeyEvent event) { }

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
        pressed.clear();
        publishIfChanged();
    }

    @Override
    public void close() {
        source.removeKeyListener(this);
        source.removeFocusListener(focusReset);
    }

    private boolean isControl(int keyCode) {
        return ControlBindings.LEFT.contains(keyCode)
                || ControlBindings.RIGHT.contains(keyCode)
                || ControlBindings.JUMP.contains(keyCode)
                || ControlBindings.RANGED.contains(keyCode)
                || ControlBindings.MELEE.contains(keyCode);
    }

    private void publishIfChanged() {
        PlayerInput current = new PlayerInput(
                pressed.stream().anyMatch(ControlBindings.LEFT::contains),
                pressed.stream().anyMatch(ControlBindings.RIGHT::contains),
                pressed.stream().anyMatch(ControlBindings.JUMP::contains),
                pressed.stream().anyMatch(ControlBindings.RANGED::contains),
                pressed.stream().anyMatch(ControlBindings.MELEE::contains));
        if (!current.equals(last)) {
            last = current;
            listener.accept(current);
        }
    }
}