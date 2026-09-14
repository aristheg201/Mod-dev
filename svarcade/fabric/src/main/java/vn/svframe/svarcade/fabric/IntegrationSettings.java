package vn.svframe.svarcade.fabric;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.JsonSchema;
import vn.svframe.svarcade.config.*;

/** Bounded off-thread loader for optional integration configuration. */
record IntegrationSettings(boolean cobblemonEnabled, int cobblemonMaxParty,
                           boolean cobblemonHeldItem, boolean cobblemonAspects, boolean cobblemonMoves,
                           boolean cobblemonRealRenderer,
                           boolean luckPermsEnabled, Id permissionRewardProvider,
                           boolean placeholderEnabled, String placeholderNamespace,
                           boolean economyEnabled,
                           boolean svQuestEnabled, Id svQuestRewardProvider,
                           boolean svFrameEnabled, boolean svFramePlayerStateBridge, boolean svFrameStatsBridge) {
    private static final long MAX_FILE = 65_536;

    static IntegrationSettings load(Path directory) {
        Node cobblemon = read(directory, "cobblemon.yml");
        cobblemon.only("enabled", "required_version", "party_snapshot", "renderer");
        String required = cobblemon.string("required_version").trim().replaceAll("\\s+", " ");
        if (!required.equals(">=1.8.1 <1.9.0")) throw cobblemon.error("required_version", "Supported contract is >=1.8.1 <1.9.0");
        Node party = cobblemon.node("party_snapshot");
        party.only("max_party_size", "include_held_item", "include_aspects", "include_moves");
        int maxParty = (int) party.integer("max_party_size", 1, 64);
        Node renderer = cobblemon.node("renderer"); renderer.only("use_real_pokemon_entity", "mutate_party_pokemon");
        if (renderer.bool("mutate_party_pokemon", false)) throw renderer.error("mutate_party_pokemon", "SVArcade integrations must not mutate party Pokemon");

        Node luck = read(directory, "luckperms.yml"); luck.only("enabled", "permission_reward_provider", "missing_behavior");
        Id permissionProvider = Id.of(luck.string("permission_reward_provider"));

        Node placeholder = read(directory, "placeholder.yml"); placeholder.only("enabled", "namespace", "missing_behavior");
        String namespace = placeholder.string("namespace");
        if (!namespace.matches("[a-z0-9_.-]{1,64}")) throw placeholder.error("namespace", "Invalid placeholder namespace");

        Node economy = read(directory, "economy.yml"); economy.only("enabled", "allow_match_purchases", "external_rewards_only", "missing_behavior");
        if (economy.bool("allow_match_purchases", false)) throw economy.error("allow_match_purchases", "External economy cannot back authoritative match currency");
        if (!economy.bool("external_rewards_only", true)) throw economy.error("external_rewards_only", "External economy is supported only as a typed reward provider");

        Node svquest = read(directory, "svquest.yml"); svquest.only("enabled", "reward_provider", "missing_behavior");
        Id svQuestProvider = Id.of(svquest.string("reward_provider"));

        Node svframe = read(directory, "svframe.yml"); svframe.only("enabled", "player_state_bridge", "stats_bridge", "missing_behavior");
        String playerState = autoMode(svframe, "player_state_bridge"), stats = autoMode(svframe, "stats_bridge");

        return new IntegrationSettings(
                cobblemon.bool("enabled", true), maxParty,
                party.bool("include_held_item", true), party.bool("include_aspects", true), party.bool("include_moves", true),
                renderer.bool("use_real_pokemon_entity", true),
                luck.bool("enabled", true), permissionProvider,
                placeholder.bool("enabled", true), namespace,
                economy.bool("enabled", true),
                svquest.bool("enabled", true), svQuestProvider,
                svframe.bool("enabled", true), playerState.equals("auto"), stats.equals("auto"));
    }

    private static String autoMode(Node node, String key) {
        String value = node.string(key);
        if (!value.equals("auto") && !value.equals("off")) throw node.error(key, "Expected auto or off");
        return value;
    }

    private static Node read(Path directory, String name) {
        Path root = directory.toAbsolutePath().normalize(), file = root.resolve(name).normalize();
        if (!file.startsWith(root) || Files.isSymbolicLink(file)) throw new ConfigException("Unsafe integration config: " + name);
        try {
            long size = Files.size(file); if (size < 1 || size > MAX_FILE) throw new ConfigException("Integration config size: " + name);
            String yaml = Files.readString(file, StandardCharsets.UTF_8);
            LoadSettings settings = LoadSettings.builder().setLabel(name).setSchema(new JsonSchema()).build();
            Object raw = new Load(settings).loadFromString(yaml);
            return new Node(raw, "integrations." + name.substring(0, name.length() - 4));
        } catch (IOException e) { throw new ConfigException("Cannot read integration config: " + name, e); }
    }
}
