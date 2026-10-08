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
            "professor_hale", "daycare_mira", "pokemall_ren", "bicycle_tomo", "fashion_elle",
            "mara_voss", "dr_orin", "rook", "selene_kade", "aurelia",
            "rocket_grunt_01", "rocket_grunt_02", "rocket_grunt_03", "rocket_admin_vex",
            "lab_scientist_iris", "archaeologist_marlow",
            "battle_tower_receptionist", "battle_tower_trainer_01", "battle_tower_trainer_02", "battle_tower_trainer_03",
            "royal_league_receptionist", "league_elite_01", "league_elite_02", "league_elite_03",
            "school_wolf_gatekeeper", "school_wolf_trainer_01", "school_wolf_trainer_02",
            "school_wolf_trainer_03", "school_wolf_master",
            "sixth_warden", "seventh_warden", "harbour_marshal_liora", "captain_dorian", "mysterious"
    };

    private static final Map<String, String> SPECIAL_ACTOR_TEXTURES = Map.of(
            "mysterious", "assets/cobblemonworld/textures/entity/boss/mysterious_figure.png"
    );

    private final Map<String, Definition> definitions = new LinkedHashMap<>();

    private NpcDefinitionRegistry() {}

    public void loadBuiltIns() {
        definitions.clear();
        int normal = 0;
        int special = 0;
        for (String id : BUILT_INS) {
            String path = "data/cobblemonworld/npcs/" + id + ".json";
            try (var stream = NpcDefinitionRegistry.class.getClassLoader().getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing NPC definition: " + path);
                Definition definition = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Definition.class);
                if (definition == null || definition.id() == null || definition.id().isBlank()) {
                    throw new IllegalStateException("Invalid NPC definition: " + path);
                }
                validateAppearance(definition);
                if (definition.specialActor()) special++; else normal++;
                definitions.put(definition.id(), definition);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load " + path, e);
            }
        }
        CobblemonWorldMod.LOGGER.info("Loaded {} Cobblemon World NPC definitions.", definitions.size());
        System.out.println("CWORLD_NPC_APPEARANCES_PASS count=" + definitions.size()
                + " normal=" + normal + " special=" + special);
    }

    private static void validateAppearance(Definition definition) {
        if (definition.specialActor()) {
            String texture = SPECIAL_ACTOR_TEXTURES.get(definition.id());
            if (texture == null || texture.isBlank()) {
                throw new IllegalStateException("Special actor has no authored appearance mapping: " + definition.id());
            }
            validatePng(texture, definition.id(), false);
            return;
        }

        if (definition.skin() == null || definition.skin().isBlank()) {
            throw new IllegalStateException("NPC has no authored skin: " + definition.id());
        }
        validatePng("assets/cobblemonworld/textures/entity/npc/" + definition.skin() + ".png", definition.id(), true);
    }

    private static void validatePng(String path, String npcId, boolean requireSkinSize) {
        try (var stream = NpcDefinitionRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing authored appearance " + path + " for " + npcId);
            byte[] header = stream.readNBytes(24);
            if (header.length < 24
                    || (header[0] & 0xFF) != 0x89 || header[1] != 0x50 || header[2] != 0x4E || header[3] != 0x47) {
                throw new IllegalStateException("Appearance is not a valid PNG: " + path);
            }
            int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                    | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
            int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                    | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
            if (requireSkinSize && (width != 64 || height != 64)) {
                throw new IllegalStateException("NPC skin must be 64x64: " + path + " was " + width + "x" + height);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not validate authored appearance " + path, e);
        }
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
            String skin,
            boolean rematchable,
            boolean specialActor,
            String interactionFlag,
            String[] flagsOnInteract,
            String questUnlockOnInteract
    ) {}
}
