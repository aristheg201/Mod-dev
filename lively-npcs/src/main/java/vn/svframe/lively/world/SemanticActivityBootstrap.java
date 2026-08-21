package vn.svframe.lively.world;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.model.NpcState;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.schedule.ScheduleEngine;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns detected block/structure capabilities into bounded semantic NPC activity.
 * It never opens/mutates containers, block entities or world blocks.
 */
public final class SemanticActivityBootstrap implements ModInitializer {
    private static final Set<String> WORK_CAPABILITIES = Set.of(
            "craft", "smelt", "cook", "brew", "smith", "repair", "read", "teach", "farm", "trade", "storage");

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (server.getTicks() % 100L != 0L || LivelyApi.npcs() == null || LivelyApi.states() == null) return;
        boolean remember = server.getTicks() % 600L == 0L;
        for (NpcDefinition npc : LivelyApi.npcs().snapshot().values()) {
            if (!npc.spawned() || !npc.aiEnabled()) continue;
            Vec3d position = LivelyApi.npcs().position(npc.id()).orElse(null);
            String worldKey = LivelyApi.npcs().worldKey(npc.id()).orElse(npc.world());
            if (position == null || worldKey == null) continue;
            SemanticStructureRegistry.Structure structure = LivelyApi.structures().at(worldKey, position.x, position.y, position.z)
                    .stream().findFirst().orElse(null);
            if (structure == null || structure.state() == SemanticStructureRegistry.OperationalState.CLOSED
                    || structure.state() == SemanticStructureRegistry.OperationalState.RESTRICTED
                    || structure.state() == SemanticStructureRegistry.OperationalState.UNDER_INVESTIGATION) continue;
            ServerWorld world = world(server, worldKey);
            if (world == null) continue;

            ActorId actor = new ActorId(npc.id(), ActorId.Kind.NPC);
            int minute = minuteOfDay(world.getTimeOfDay());
            ScheduleEngine.ScheduleEntry schedule = LivelyApi.schedules().current(actor, minute).orElse(null);
            String activity = schedule == null ? "" : schedule.activity().toLowerCase(Locale.ROOT);
            NpcState state = LivelyApi.states().get(npc.id()).orElse(null);
            if (state == null) continue;

            if (structure.capabilities().contains("sleep") && (contains(activity, "sleep", "rest", "home") || highNeed(state, "fatigue", .72D))) {
                changeNeed(state, "fatigue", -.055D);
                if (remember) state.remember("semantic_sleep", Map.of("structure", structure.id(), "capability", "sleep"), .14D, 1D);
                continue;
            }

            if (structure.capabilities().contains("gather") && (contains(activity, "social", "gather", "meet") || highNeed(state, "social", .72D))) {
                changeNeed(state, "social", -.045D);
                if (remember) state.remember("semantic_gather", Map.of("structure", structure.id(), "capability", "gather"), .12D, 1D);
            }

            String workCapability = structure.capabilities().stream().filter(WORK_CAPABILITIES::contains).sorted().findFirst().orElse(null);
            if (workCapability != null && isWorkActivity(activity, npc, structure)) {
                applyOccupationNeeds(npc, state);
                if (remember) state.remember("semantic_work", Map.of(
                        "structure", structure.id(), "capability", workCapability,
                        "activity", schedule == null ? "work" : schedule.activity()), .16D, 1D);
            }
        }
    }

    private static boolean isWorkActivity(String activity, NpcDefinition npc, SemanticStructureRegistry.Structure structure) {
        if (contains(activity, "work", "craft", "smelt", "cook", "brew", "smith", "teach", "read", "farm", "trade")) return true;
        String workplace = npc.metadata().get("work.structure");
        return workplace != null && workplace.equals(structure.id());
    }

    private static void applyOccupationNeeds(NpcDefinition npc, NpcState state) {
        String occupation = npc.metadata().get("occupation.id");
        if (occupation == null || occupation.isBlank()) {
            changeNeed(state, "purpose", -.015D);
            changeNeed(state, "fatigue", .006D);
            return;
        }
        Optional<ScheduleEngine.Occupation> definition = LivelyApi.schedules().occupation(occupation);
        if (definition.isEmpty()) return;
        definition.get().needsImpact().forEach((need, delta) -> changeNeed(state, need, Math.max(-.10D, Math.min(.10D, delta))));
    }

    private static boolean highNeed(NpcState state, String key, double threshold) {
        return state.snapshot(1).need(key) >= threshold;
    }

    private static void changeNeed(NpcState state, String key, double delta) {
        NpcSnapshot snapshot = state.snapshot(1);
        state.setNeed(key, Math.max(0D, Math.min(1D, snapshot.need(key) + delta)));
    }

    private static boolean contains(String value, String... terms) {
        if (value == null || value.isBlank()) return false;
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private static int minuteOfDay(long time) {
        long ticks = Math.floorMod(time + 6000L, 24000L);
        return (int) (ticks * 1440L / 24000L);
    }

    private static ServerWorld world(MinecraftServer server, String raw) {
        Identifier id = Identifier.tryParse(raw);
        return id == null ? null : server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, id));
    }
}
