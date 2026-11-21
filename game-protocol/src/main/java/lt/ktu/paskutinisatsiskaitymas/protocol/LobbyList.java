package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;

/** A bounded page of open rooms. The last room's code is the next cursor. */
public record LobbyList(List<LobbySummary> rooms, boolean hasMore) implements Message {
    public static final int PAGE_SIZE = 24;

    public LobbyList {
        rooms = List.copyOf(rooms);
        if (rooms.size() > PAGE_SIZE || hasMore && rooms.size() != PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid room list size");
        }
        for (int index = 1; index < rooms.size(); index++) {
            if (rooms.get(index - 1).code().compareTo(rooms.get(index).code()) >= 0) {
                throw new IllegalArgumentException("Room codes must be in ascending order");
            }
        }
    }
}
