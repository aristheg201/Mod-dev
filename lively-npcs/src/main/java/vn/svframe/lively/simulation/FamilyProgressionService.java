package vn.svframe.lively.simulation;

import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.social.RomanceEngine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Reconciles married bonds and authored parent links into persistent households/kinship. */
public final class FamilyProgressionService {
    private static final long PULSE_TICKS = 1200L;
    private final ConcurrentHashMap<String, Boolean> linkedParents = new ConcurrentHashMap<>();
    private long lastPulse;

    public void tick(long tick) {
        if (tick - lastPulse < PULSE_TICKS) return;
        lastPulse = tick;
        reconcileMarriages();
        reconcileAuthoredParents();
    }

    private void reconcileMarriages() {
        for (RomanceEngine.Bond bond : LivelyApi.romance().snapshot().values()) {
            if (bond.stage() != RomanceEngine.Stage.MARRIED) continue;
            LivelyApi.family().ensureSpouseHousehold(bond.a(), bond.b(), commonHome(bond.a(), bond.b()));
        }
    }

    private void reconcileAuthoredParents() {
        if (LivelyApi.npcs() == null) return;
        for (NpcDefinition definition : LivelyApi.npcs().snapshot().values()) {
            String raw = definition.metadata().get("family.parent");
            if (raw == null || raw.isBlank()) continue;
            try {
                UUID parentId = UUID.fromString(raw);
                String linkKey = parentId + ">" + definition.id();
                if (linkedParents.putIfAbsent(linkKey, Boolean.TRUE) != null) continue;
                LivelyApi.family().linkParentChild(new ActorId(parentId, ActorId.Kind.NPC),
                        new ActorId(definition.id(), ActorId.Kind.NPC), 1D, Map.of("source", "npc_metadata"));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private String commonHome(ActorId a, ActorId b) {
        if (LivelyApi.npcs() == null) return null;
        String left = LivelyApi.npcs().get(a.uuid()).map(d -> d.metadata().get("home.structure")).orElse(null);
        String right = LivelyApi.npcs().get(b.uuid()).map(d -> d.metadata().get("home.structure")).orElse(null);
        if (left != null && left.equals(right)) return left;
        return left != null ? left : right;
    }
}
