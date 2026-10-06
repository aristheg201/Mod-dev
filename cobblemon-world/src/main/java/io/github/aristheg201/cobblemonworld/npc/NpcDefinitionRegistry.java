package io.github.aristheg201.cobblemonworld.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NpcDefinitionRegistry {
    public static final NpcDefinitionRegistry INSTANCE = new NpcDefinitionRegistry();
    private static final Gson GSON = new GsonBuilder().create();

    private static final String[] BUILT_INS = {
            "mara_voss", "dr_orin", "rook", "selene_kade", "aurelia",
            "sixth_warden", "seventh_warden", "resonance_heart", "mysterious"
    };

    private final Map<String, Definition> definitions = new LinkedHashMap<>();

    private NpcDefinitionRegistry() {}

    public void loadBuiltIns() {
        definitions.clear();
        for (String id : BUILT_INS) {
            String path = "data/cobblemonworld/npcs/" + id + ".json";
            try (var stream = NpcDefinitionRegistry.class.getClassLoader().getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing NPC definition: " + path);
                Definition definition = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Definition.class);
                if (definition == null || definition.id() == null || definition.id().isBlank()) {
                    throw new IllegalStateException("Invalid NPC definition: " + path);
                }
                definitions.put(definition.id(), definition);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load " + path, e);
            }
        }
        CobblemonWorldMod.LOGGER.info("Loaded {} Cobblemon World NPC definitions.", definitions.size());
    }

    public Definition get(String id) { return definitions.get(id); }
    public Collection<Definition> all() { return definitions.values(); }

    public record Definition(
            String id,
            String displayName,
            String npcClass,
            int visualLevel,
            int skill,
            String preBattleText,
            String postBattleText,
            String[] team,
            String defeatFlag,
            String[] flagsOnDefeat,
            String contactUnlock,
            String chapterOnDefeat,
            String requiredChapter,
            String[] requiredFlags,
            String badge,
            boolean rematchable,
            boolean specialActor
    ) {}
}
