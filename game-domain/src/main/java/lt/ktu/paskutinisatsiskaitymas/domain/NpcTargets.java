package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The one live NPC owner. Its iterator is the JDK map-values iterator. */
public final class NpcTargets implements Iterable<NPC> {
    private final LinkedHashMap<Integer, NPC> npcs = new LinkedHashMap<>();
    private final Map<Integer, NPC> view = Collections.unmodifiableMap(npcs);

    void put(int id, NPC npc) { npcs.put(id, npc); }
    NPC get(int id) { return npcs.get(id); }
    NPC remove(int id) { return npcs.remove(id); }
    boolean containsKey(int id) { return npcs.containsKey(id); }
    Collection<NPC> values() { return view.values(); }
    Set<Map.Entry<Integer, NPC>> entrySet() { return view.entrySet(); }
    Map<Integer, NPC> view() { return view; }
    void removeInactive() { npcs.values().removeIf(npc -> !npc.alive()); }

    @Override
    public Iterator<NPC> iterator() { return npcs.values().iterator(); }
}
