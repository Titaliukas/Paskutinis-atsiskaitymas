package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.Objects;

/** An arena description; this slice supplies no layout, dimensions or collision behavior. */
public record Arena(String name, List<Platform> platforms) {
    public Arena {
        Objects.requireNonNull(name, "name");
        platforms = List.copyOf(platforms);
    }
}
