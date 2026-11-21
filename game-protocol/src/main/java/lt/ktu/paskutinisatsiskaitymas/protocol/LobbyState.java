package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LobbyState(String code, UUID hostId, Phase phase, List<LobbyMember> members) implements Message {
    public enum Phase { WAITING, RUNNING }

    public LobbyState {
        code = LobbyCode.normalize(code);
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(phase, "phase");
        members = List.copyOf(members);
        if (members.isEmpty() || members.size() > 4
                || phase == Phase.RUNNING && members.stream().anyMatch(member -> member.character() == null)
                || members.stream().noneMatch(member -> member.playerId().equals(hostId))
                || members.stream().map(LobbyMember::playerId).distinct().count() != members.size()
                || members.stream().map(LobbyMember::slot).distinct().count() != members.size()) {
            throw new IllegalArgumentException("Invalid lobby roster");
        }
    }
}
