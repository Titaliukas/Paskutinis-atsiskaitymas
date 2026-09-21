package lt.ktu.paskutinisatsiskaitymas.protocol;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Thread-safe after construction; serializes transport DTOs only, using a fixed type allowlist. */
public final class JsonMessageCodec {
    public static final int MAX_MESSAGE_CHARACTERS = 8192;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public String encode(Message message) throws ProtocolException {
        if (message == null) {
            throw new ProtocolException("Message is required", null);
        }
        try {
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new ProtocolException("Cannot encode message", exception);
        }
    }

    public Message decode(String json) throws ProtocolException {
        if (json == null || json.length() > MAX_MESSAGE_CHARACTERS) {
            throw new ProtocolException("Missing or oversized message", null);
        }
        try {
            Message message = mapper.readValue(json, Message.class);
            if (message == null) {
                throw new ProtocolException("Message is required", null);
            }
            return message;
        } catch (JsonProcessingException exception) {
            throw new ProtocolException("Invalid message", exception);
        }
    }
}
