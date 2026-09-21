package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.event.KeyEvent;
import java.util.Set;

/** Single client-side location for configurable prototype controls. */
final class ControlBindings {
    static final Set<Integer> LEFT = Set.of(KeyEvent.VK_A);
    static final Set<Integer> RIGHT = Set.of(KeyEvent.VK_D);
    static final Set<Integer> JUMP = Set.of(KeyEvent.VK_SPACE, KeyEvent.VK_W);

    private ControlBindings() { }
}
