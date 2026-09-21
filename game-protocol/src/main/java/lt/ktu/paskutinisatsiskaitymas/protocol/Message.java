package lt.ktu.paskutinisatsiskaitymas.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Closed wire-message vocabulary. Explicit names prevent deserialization of arbitrary Java types. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Hello.class, name = "HELLO"),
    @JsonSubTypes.Type(value = Welcome.class, name = "WELCOME"),
    @JsonSubTypes.Type(value = Ping.class, name = "PING"),
    @JsonSubTypes.Type(value = Pong.class, name = "PONG"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR")
})
public sealed interface Message permits Hello, Welcome, Ping, Pong, ErrorMessage {
}
