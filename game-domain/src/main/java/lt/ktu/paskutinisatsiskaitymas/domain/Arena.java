package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.Objects;

/** Rectangular world bounds and solid platforms, with origin at the top-left and positive Y downward. */
public record Arena(String name, double width, double height, List<Platform> platforms) {
    public Arena {
        Objects.requireNonNull(name, "name");
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Arena dimensions must be finite and positive");
        }
        platforms = List.copyOf(platforms);
    }
}
