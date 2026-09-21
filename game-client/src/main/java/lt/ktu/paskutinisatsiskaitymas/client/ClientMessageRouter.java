package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.function.Consumer;
import lt.ktu.paskutinisatsiskaitymas.protocol.ErrorMessage;
import lt.ktu.paskutinisatsiskaitymas.protocol.Message;
import lt.ktu.paskutinisatsiskaitymas.protocol.Pong;
import lt.ktu.paskutinisatsiskaitymas.protocol.ProtocolException;
import lt.ktu.paskutinisatsiskaitymas.protocol.Welcome;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** Server-message dispatch boundary, independent of rendering and input. */
final class ClientMessageRouter {
    void route(Message message, Consumer<Welcome> welcome,
            Consumer<WorldSnapshot> snapshot, Consumer<ErrorMessage> error) throws ProtocolException {
        if (message instanceof Welcome value) {
            welcome.accept(value);
        } else if (message instanceof WorldSnapshot value) {
            snapshot.accept(value);
        } else if (message instanceof ErrorMessage value) {
            error.accept(value);
        } else if (!(message instanceof Pong)) {
            throw new ProtocolException("Expected a server message", null);
        }
    }
}
