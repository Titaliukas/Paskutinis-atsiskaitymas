package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Objects;

/** Detached state and truthful authoritative activity for transport mapping and demonstrations. */
public record NPCView(NPCState state, NPCActivity activity) {
    public NPCView {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(activity, "activity");
    }
}
