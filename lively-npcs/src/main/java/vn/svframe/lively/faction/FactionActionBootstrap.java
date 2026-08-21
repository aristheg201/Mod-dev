package vn.svframe.lively.faction;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.actor.ActorSnapshot;
import vn.svframe.lively.api.LivelyApi;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Executes the strategic intent chosen by CausalSimulationService as semantic faction consequences. */
public final class FactionActionBootstrap implements ModInitializer {
    private static final long ACTION_COOLDOWN_TICKS = 6000L;
    private static final int MAX_FACTIONS_PER_PULSE = 64;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 1200L == 0L) pulse(server.getTicks());
        });
    }

    int pulse(long tick) {
        FactionEngine.Snapshot snapshot = LivelyApi.factions().snapshot();
        if (snapshot.factions().isEmpty()) return 0;
        Set<ActorId> assigned = new HashSet<>();
        snapshot.factions().values().forEach(faction -> assigned.addAll(faction.members()));
        int executed = 0;
        for (FactionEngine.Faction faction : snapshot.factions().values().stream()
                .sorted(Comparator.comparing(value -> value.id().toString())).limit(MAX_FACTIONS_PER_PULSE).toList()) {
            String strategy = faction.knowledge().get("current_strategy");
            if (strategy == null || strategy.isBlank()) continue;
            long last = longValue(faction.knowledge().get("last_strategy_tick"), Long.MIN_VALUE / 4L);
            if (tick - last < ACTION_COOLDOWN_TICKS) continue;
            boolean changed = switch (strategy) {
                case "increase_patrol" -> increasePatrol(faction, tick);
                case "secure_supply" -> secureSupply(faction, tick);
                case "recruit" -> recruit(faction, assigned, tick);
                default -> false;
            };
            if (changed) executed++;
        }
        return executed;
    }

    private boolean increasePatrol(FactionEngine.Faction faction, long tick) {
        long gain = Math.max(1L, Math.min(25L, 2L + faction.members().size() / 8L));
        if (LivelyApi.factions().adjustResource(faction.id(), "security", gain).isEmpty()) return false;
        markAction(faction.id(), tick, "patrol", "security+" + gain);
        if (LivelyApi.states() != null) {
            faction.members().stream().filter(actor -> actor.kind() == ActorId.Kind.NPC).limit(16).forEach(actor ->
                    LivelyApi.states().get(actor.uuid()).ifPresent(state -> state.remember("faction_patrol_assignment",
                            Map.of("faction", faction.id().toString(), "strategy", "increase_patrol"), .30D, 1D)));
        }
        return true;
    }

    private boolean secureSupply(FactionEngine.Faction faction, long tick) {
        long gain = Math.max(1L, Math.min(50L, 4L + faction.members().size() / 4L));
        if (LivelyApi.factions().adjustResource(faction.id(), "supplies", gain).isEmpty()) return false;
        markAction(faction.id(), tick, "supply", "supplies+" + gain);
        return true;
    }

    private boolean recruit(FactionEngine.Faction faction, Set<ActorId> assigned, long tick) {
        ActorId candidate = LivelyApi.actors().snapshot().actors().values().stream()
                .filter(actor -> actor.id().kind() == ActorId.Kind.NPC)
                .filter(actor -> !assigned.contains(actor.id()))
                .sorted(Comparator.comparingDouble(FactionActionBootstrap::recruitScore).reversed()
                        .thenComparing(actor -> actor.id().uuid().toString()))
                .map(ActorSnapshot::id).findFirst().orElse(null);
        if (candidate == null || LivelyApi.factions().addMember(faction.id(), candidate).isEmpty()) {
            markAction(faction.id(), tick, "recruit", "no_candidate");
            return false;
        }
        assigned.add(candidate);
        LivelyApi.actors().get(candidate).ifPresent(actor -> {
            Map<String, String> facts = new HashMap<>(actor.facts());
            facts.put("faction", faction.id().toString());
            LivelyApi.actors().upsert(actor.id(), actor.displayName(), actor.socialStats(), facts, actor.tags());
        });
        if (LivelyApi.states() != null) {
            LivelyApi.states().get(candidate.uuid()).ifPresent(state -> state.remember("joined_faction",
                    Map.of("faction", faction.id().toString(), "name", faction.name()), .60D, 1D));
        }
        markAction(faction.id(), tick, "recruit", candidate.uuid().toString());
        return true;
    }

    private void markAction(java.util.UUID factionId, long tick, String action, String result) {
        LivelyApi.factions().updateKnowledge(factionId, "last_strategy_tick", Long.toString(tick));
        LivelyApi.factions().updateKnowledge(factionId, "last_strategy_action", action);
        LivelyApi.factions().updateKnowledge(factionId, "last_strategy_result", result);
    }

    private static double recruitScore(ActorSnapshot actor) {
        return actor.social("ambition") * .42D + actor.social("loyal") * .30D
                + actor.social("friendly") * .18D + actor.social("brave") * .10D;
    }

    private static long longValue(String raw, long fallback) {
        try { return raw == null ? fallback : Long.parseLong(raw); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
