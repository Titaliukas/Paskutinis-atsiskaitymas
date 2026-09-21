package lt.ktu.paskutinisatsiskaitymas.protocol;

/** A malformed or unsupported wire payload; callers should return a bounded, generic error. */
public final class ProtocolException extends Exception {
    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
