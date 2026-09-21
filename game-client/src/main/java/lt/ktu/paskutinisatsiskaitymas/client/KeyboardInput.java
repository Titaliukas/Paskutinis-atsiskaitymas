package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/** Converts press/release transitions into complete input state without relying on key-repeat timing. */
final class KeyboardInput extends KeyAdapter {
    private final ClientConnection connection;
    private final Set<Integer> pressed = new HashSet<>();
    private boolean lastLeft;
    private boolean lastRight;
    private boolean lastJump;

    KeyboardInput(GamePanel panel, ClientConnection connection) {
        this.connection = connection;
        panel.addKeyListener(this);
        panel.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                reset();
            }
        });
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

    void reset() {
        if (!pressed.isEmpty() || lastLeft || lastRight || lastJump) {
            pressed.clear();
            publishIfChanged();
        }
    }

    private boolean isControl(int keyCode) {
        return ControlBindings.LEFT.contains(keyCode)
                || ControlBindings.RIGHT.contains(keyCode)
                || ControlBindings.JUMP.contains(keyCode);
    }

    private void publishIfChanged() {
        boolean left = pressed.stream().anyMatch(ControlBindings.LEFT::contains);
        boolean right = pressed.stream().anyMatch(ControlBindings.RIGHT::contains);
        boolean jump = pressed.stream().anyMatch(ControlBindings.JUMP::contains);
        if (left != lastLeft || right != lastRight || jump != lastJump) {
            lastLeft = left;
            lastRight = right;
            lastJump = jump;
            connection.sendInput(left, right, jump);
        }
    }
}
