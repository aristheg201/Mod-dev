package io.github.aristheg201.cobblemonworld.story;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.network.NavigationPayload;
import io.github.aristheg201.cobblemonworld.npc.NpcDefinitionRegistry;
import io.github.aristheg201.cobblemonworld.npc.NpcPlacementStore;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ObjectiveService {
    private static final Gson GSON = new Gson();
    private static List<Route> routes = List.of();
    private static final Map<UUID, Navigation> LAST = new HashMap<>();
    public record Route(String npc, String completeFlag, String objectiveKey) {}
    public record Navigation(String questId, String objectiveId, String label, String dimension,
                             double x, double y, double z, String status) {}
    private ObjectiveService() {}
    public static void register() {
        try (var input = ObjectiveService.class.getClassLoader().getResourceAsStream("data/cobblemonworld/progression/routes.json")) {
            routes = List.of(GSON.fromJson(new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8), Route[].class));
        } catch (Exception e) { throw new IllegalStateException("Cannot load main-objective route", e); }
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            StoryRouteService.migrate(handler.player);
            sync(handler.player, true);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) for (var player : server.getPlayerList().getPlayers()) sync(player, false);
        });
    }
    public static PinnedObjective main(PlayerProgression p) {
        if (p.narrative.schema >= 1) return io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.objective(p, "main");
        for (Route route : routes) if (!p.storyFlags.contains(route.completeFlag())) {
            return new PinnedObjective("main", route.npc(), "npc", route.npc(), "", route.objectiveKey());
        }
        return null;
    }
    private static PinnedObjective quest(PlayerProgression p, String questId) {
        var expanded = io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.objective(p, questId);
        if (expanded != null) return expanded;
        var q = ContentRegistry.INSTANCE.quest(questId);
        if (q == null || !p.activeSideQuests.contains(questId) || q.objectives() == null) return null;
        for (var o : q.objectives()) if (p.questProgress.getOrDefault(questId + ":" + o.id(), 0) < o.amount()) {
            boolean npc = NpcDefinitionRegistry.INSTANCE.get(o.target()) != null;
            return new PinnedObjective(q.id(), o.id(), npc ? "npc" : o.type(), npc ? o.target() : "", "", q.description());
        }
        return null;
    }
    public static boolean pin(ServerPlayer player, String questId) {
        var p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        var objective = quest(p, questId);
        if (objective == null && io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.activate(player, questId)) objective = quest(p,questId);
        if (objective == null) return false;
        p.pinnedObjective = objective; p.objectiveSchema = 1; p.currentObjective = objective.textKey();
        ProgressionStore.INSTANCE.save(); sync(player, true); return true;
    }
    public static int reset(ServerPlayer player) {
        var p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        p.pinnedObjective = null; p.objectiveSchema = 1;
        var main = main(p); p.currentObjective = main == null ? "" : main.textKey();
        ProgressionStore.INSTANCE.save(); sync(player, true);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("objective.cobblemonworld.reset"));
        return 1;
    }
    public static PinnedObjective resolve(PlayerProgression p) {
        int previousSchema = p.objectiveSchema;
        PinnedObjective previousPin = p.pinnedObjective;
        String previousText = p.currentObjective;
        if (p.objectiveSchema == 0) {
            for (String id : p.activeSideQuests) {
                var q = ContentRegistry.INSTANCE.quest(id);
                if (q != null && q.description().equals(p.currentObjective)) { p.pinnedObjective = quest(p, id); break; }
            }
            p.objectiveSchema = 1;
            if (p.pinnedObjective == null && !p.currentObjective.isBlank())
                CobblemonWorldMod.LOGGER.info("Migrated legacy rendered objective to safe main-story target (chapter {})", p.currentStory);
        }
        if (p.pinnedObjective != null) {
            if ("main".equals(p.pinnedObjective.questId())) p.pinnedObjective = null;
            else p.pinnedObjective = quest(p, p.pinnedObjective.questId());
        }
        var resolved = p.pinnedObjective == null ? main(p) : p.pinnedObjective;
        p.currentObjective = resolved == null ? "" : resolved.textKey();
        if (previousSchema != p.objectiveSchema || !Objects.equals(previousPin, p.pinnedObjective)
                || !Objects.equals(previousText, p.currentObjective)) ProgressionStore.INSTANCE.save();
        return resolved;
    }
    public static Navigation navigation(ServerPlayer player) {
        var p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        var o = resolve(p);
        if (o == null) return new Navigation("", "", "", "", 0, 0, 0, "none");
        if ("poi".equals(o.targetType())) {
            var point = io.github.aristheg201.cobblemonworld.narrative.PoiStore.PLACES.get(o.npcId());
            var def = io.github.aristheg201.cobblemonworld.narrative.NarrativeRegistry.INSTANCE.pois.get(o.npcId());
            String label = def == null ? "" : def.name();
            if (point == null) return new Navigation(o.questId(), o.objectiveId(), label, "", 0, 0, 0, "unavailable");
            return new Navigation(o.questId(), o.objectiveId(), label, point.dimension(), point.x(), point.y(), point.z(), point.dimension().equals(player.level().dimension().location().toString()) ? "ready" : "dimension");
        }
        var definition = NpcDefinitionRegistry.INSTANCE.get(o.npcId());
        if ("mysterious".equals(o.npcId())) {
            var cfg = io.github.aristheg201.cobblemonworld.config.CWorldConfig.INSTANCE;
            if (!cfg.finalEncounterEnabled) return new Navigation(o.questId(), o.objectiveId(), "???", "", 0, 0, 0, "unavailable");
            return new Navigation(o.questId(), o.objectiveId(), "???", cfg.finalEncounterDimension,
                    cfg.finalEncounterX, cfg.finalEncounterY, cfg.finalEncounterZ,
                    cfg.finalEncounterDimension.equals(player.level().dimension().location().toString()) ? "ready" : "dimension");
        }
        var placement = NpcPlacementStore.INSTANCE.get(o.npcId());
        String name = definition == null ? "" : definition.displayName();
        if (placement == null) return new Navigation(o.questId(), o.objectiveId(), name, "", 0, 0, 0, "unavailable");
        return new Navigation(o.questId(), o.objectiveId(), name, placement.dimension(), placement.x(), placement.y(), placement.z(),
                placement.dimension().equals(player.level().dimension().location().toString()) ? "ready" : "dimension");
    }
    public static void sync(ServerPlayer player, boolean force) {
        Navigation next = navigation(player);
        Navigation previous = LAST.put(player.getUUID(), next);
        if (force || !next.equals(previous)) ServerPlayNetworking.send(player, new NavigationPayload(GSON.toJson(next)));
    }
}
