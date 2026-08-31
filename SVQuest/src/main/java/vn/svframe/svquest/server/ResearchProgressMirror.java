package vn.svframe.svquest.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svquest.SVQuest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Mirrors Cobblemon Research Tasks 2.0 authoritative per-species task progress into SVQuest. */
public final class ResearchProgressMirror {
    private final MinecraftServer server;
    private final QuestEngine engine;
    private int ticks;

    public ResearchProgressMirror(MinecraftServer server, QuestEngine engine) {
        this.server = server;
        this.engine = engine;
    }

    public void tick() {
        if (++ticks % 100 != 0) return; // every five seconds
        if (!FabricLoader.getInstance().isModLoaded("cobblemonresearchtasks")) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try { mirror(player); }
            catch (Throwable t) { SVQuest.LOGGER.debug("Research mirror failed safely for {}: {}", player.getName().getString(), t.toString()); }
        }
    }

    private void mirror(ServerPlayerEntity player) throws Exception {
        Class<?> managerClass = Class.forName("github.jorgaomc.storage.MasteryManager");
        Object manager = managerClass.getMethod("get").invoke(null);
        String json = String.valueOf(managerClass.getMethod("buildPlayerSnapshotJson", java.util.UUID.class)
                .invoke(manager, player.getUuid()));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("species") || !root.get("species").isJsonObject()) return;

        Method getSpeciesTasks = managerClass.getMethod("getSpeciesTasks", String.class);
        for (var speciesEntry : root.getAsJsonObject("species").entrySet()) {
            String species = normalizeSpecies(speciesEntry.getKey());
            if (!speciesEntry.getValue().isJsonObject()) continue;
            JsonObject speciesState = speciesEntry.getValue().getAsJsonObject();
            JsonObject progress = speciesState.has("progress") && speciesState.get("progress").isJsonObject()
                    ? speciesState.getAsJsonObject("progress") : new JsonObject();

            Object speciesTasks = getSpeciesTasks.invoke(manager, speciesEntry.getKey());
            if (speciesTasks == null) speciesTasks = getSpeciesTasks.invoke(manager, species);
            if (speciesTasks == null) continue;
            Object tasksObject = field(speciesTasks, "tasks");
            if (!(tasksObject instanceof Iterable<?> tasks)) continue;

            for (Object task : tasks) {
                String id = stringField(task, "id");
                String type = String.valueOf(field(task, "type")).toUpperCase(Locale.ROOT);
                long value = jsonLong(progress.get(id));
                Map<String, String> meta = new HashMap<>();
                meta.put("species", species);
                meta.put("target", species);

                switch (type) {
                    case "CAPTURE_THIS" -> engine.absolute(player, "RESEARCH_CAPTURE", value, meta);
                    case "EVOLVE_THIS" -> engine.absolute(player, "RESEARCH_EVOLVE", value, meta);
                    case "LEVEL_UP_COUNT" -> engine.absolute(player, "RESEARCH_LEVEL_UP", value, meta);
                    case "FRIENDSHIP_AT_LEAST" -> engine.absolute(player, "RESEARCH_FRIENDSHIP", value, meta);
                    case "FISH_THIS" -> engine.absolute(player, "RESEARCH_FISH", value, meta);
                    case "DEFEAT_ANY_COUNT" -> {
                        meta.put("actor_species", species);
                        engine.absolute(player, "RESEARCH_DEFEAT", value, meta);
                    }
                    case "DEFEAT_SPECIES" -> {
                        meta.put("actor_species", species);
                        meta.put("target_species", normalizeSpecies(stringField(task, "targetSpecies")));
                        engine.absolute(player, "RESEARCH_DEFEAT", value, meta);
                    }
                    case "DEFEAT_TYPE_COUNT" -> {
                        meta.put("actor_species", species);
                        meta.put("target_types", normalizeSpecies(stringField(task, "targetType")));
                        engine.absolute(player, "RESEARCH_DEFEAT", value, meta);
                    }
                    default -> { }
                }
            }
        }
    }

    private static Object field(Object target, String name) {
        if (target == null) return null;
        try {
            Field f = target.getClass().getField(name);
            return f.get(target);
        } catch (Throwable ignored) { return null; }
    }

    private static String stringField(Object target, String name) {
        Object value = field(target, name);
        return value == null ? "" : String.valueOf(value);
    }

    private static long jsonLong(JsonElement e) {
        if (e == null || e.isJsonNull()) return 0;
        try { return Math.max(0, e.getAsLong()); } catch (Throwable ignored) { return 0; }
    }

    private static String normalizeSpecies(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        return s;
    }
}
