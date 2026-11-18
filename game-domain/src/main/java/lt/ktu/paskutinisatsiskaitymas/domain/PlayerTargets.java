package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns exactly four slots; the lookup index always contains the same objects as the array. */
public final class PlayerTargets implements Iterable<GameCharacter> {
    private final GameCharacter[] slots = new GameCharacter[4];
    private final Map<UUID, GameCharacter> byPlayer = new LinkedHashMap<>();
    private final Map<UUID, GameCharacter> view = Collections.unmodifiableMap(byPlayer);

    void add(GameCharacter character) {
        int slot = character.state().slot();
        if (slot < 0 || slot >= slots.length || slots[slot] != null || byPlayer.containsKey(character.playerId())) {
            throw new IllegalArgumentException("Player ID and slot must be unique and fit four slots");
        }
        slots[slot] = character;
        byPlayer.put(character.playerId(), character);
    }

    GameCharacter remove(UUID playerId) {
        GameCharacter removed = byPlayer.remove(playerId);
        if (removed != null) {
            slots[removed.state().slot()] = null;
        }
        return removed;
    }

    GameCharacter get(UUID playerId) { return byPlayer.get(playerId); }
    boolean containsKey(UUID playerId) { return byPlayer.containsKey(playerId); }
    int size() { return byPlayer.size(); }
    Collection<GameCharacter> values() { return view.values(); }
    Set<Map.Entry<UUID, GameCharacter>> entrySet() { return view.entrySet(); }
    Map<UUID, GameCharacter> view() { return view; }

    @Override
    public Iterator<GameCharacter> iterator() { return new PlayerTargetIterator(slots); }
}
