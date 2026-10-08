package lt.ktu.paskutinisatsiskaitymas.domain;

/** Algorithm inputs are detached snapshots; seconds is a positive simulation duration. */
public interface NPCActivityStrategy {
    // STRATEGY-REPORT R1 BEGIN: One contract shared by four interchangeable algorithms.
    NPCAction decide(NPCState self, NPCTarget target, double seconds);
    // STRATEGY-REPORT R1 END
}
