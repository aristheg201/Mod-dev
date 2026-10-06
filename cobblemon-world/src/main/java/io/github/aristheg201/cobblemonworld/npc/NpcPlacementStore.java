package io.github.aristheg201.cobblemonworld.npc;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NpcPlacementStore {
    public static final NpcPlacementStore INSTANCE = new NpcPlacementStore();
    private final Map<String, NpcPlacement> placements = new LinkedHashMap<>();
    private NpcPlacementStore() {}
    public void put(NpcPlacement p) { placements.put(p.id(), p); }
    public NpcPlacement get(String id) { return placements.get(id); }
    public Map<String, NpcPlacement> all() { return Map.copyOf(placements); }
    public boolean remove(String id) { return placements.remove(id) != null; }
}
