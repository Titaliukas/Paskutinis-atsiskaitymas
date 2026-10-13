package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.function.Consumer;

/** Device-independent source of player input; the Adapter target for concrete input devices. */
interface InputSource {
    PlayerInput currentInput();

    void setListener(Consumer<PlayerInput> onChange);

    void reset();
}
