package vn.svframe.svrelationships.fabric.reward;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.localization.MessageService;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.relationship.RelationshipState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Config-driven automatic relationship rewards anchored to Minecraft world time.
 * Each definition is idempotent per relationship and Minecraft day through the
 * normal reward-claim repository, so restarts and repeated evaluations cannot
 * duplicate a gift.
 */
public final class AutomaticRelationshipRewardService {
    private static final long DAY_TICKS = 24_000L;

    private final Path root;
    private final MinecraftServer server;
    private final GameplayDefinitionService gameplay;
    private final RelationshipService relationships;
    private final RelationshipRewardService rewards;
    private final MessageService messages;
    private final AtomicReference<List<Definition>> definitions = new AtomicReference<>(List.of());
    private final Map<String, Long> processedDays = new ConcurrentHashMap<>();
    private long serverTicks;

    public AutomaticRelationshipRewardService(
            Path root,
            MinecraftServer server,
            GameplayDefinitionService gameplay,
            RelationshipService relationships,
            RelationshipRewardService rewards,
            MessageService messages
    ) {
        this.root = Objects.requireNonNull(root, "root");
        this.server = Objects.requireNonNull(server, "server");
        this.gameplay = Objects.requireNonNull(gameplay, "gameplay");
        this.relationships = Objects.requireNonNull(relationships, "relationships");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public void initialize() {
        try {
            Path target = root.resolve("automatic-rewards.yml");
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                try (InputStream input = AutomaticRelationshipRewardService.class.getResourceAsStream("/defaults/automatic-rewards.yml")) {
                    if (input == null) throw new IOException("Missing bundled resource: /defaults/automatic-rewards.yml");
                    Files.copy(input, target);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to install automatic reward configuration", exception);
        }
        ReloadResult result = reload();
        if (!result.success()) throw new IllegalStateException("Unable to load automatic reward configuration: " + result.detail());
    }

    public ReloadResult reload() {
        try {
            List<Definition> parsed = parse(root.resolve("automatic-rewards.yml"));
            for (Definition definition : parsed) {
                var profile = gameplay.snapshot().rewardProfiles().get(definition.rewardProfile());
                if (profile == null) throw new IllegalArgumentException("Unknown reward profile: " + definition.rewardProfile());
                if (!"automatic_minecraft_time".equals(profile.triggerType())) {
                    throw new IllegalArgumentException("Automatic reward profile must use trigger_type automatic_minecraft_time: " + definition.rewardProfile());
                }
            }
            definitions.set(List.copyOf(parsed));
            processedDays.clear();
            return new ReloadResult(true, "");
        } catch (Exception exception) {
            return new ReloadResult(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    public void tick() {
        serverTicks++;
        for (Definition definition : definitions.get()) {
            int interval = definition.evaluationIntervalTicks();
            if (Math.floorMod(serverTicks + definition.id().hashCode(), interval) != 0) continue;

            ServerWorld world = resolveWorld(definition.clockDimension());
            if (world == null) continue;
            long timeOfDay = world.getTimeOfDay();
            long day = Math.floorDiv(timeOfDay, DAY_TICKS);
            int dayTick = (int) Math.floorMod(timeOfDay, DAY_TICKS);
            if (!insideWindow(dayTick, definition.startTick(), definition.endTick())) continue;

            for (var player : server.getPlayerManager().getPlayerList()) {
                String processedKey = definition.id() + ":" + player.getUuid();
                if (processedDays.getOrDefault(processedKey, Long.MIN_VALUE) == day) continue;

                int delivered = 0;
                boolean retryRequired = false;
                List<RelationshipState> states = relationships.states(player.getUuid());
                for (RelationshipState relationship : states) {
                    if (definition.requirePartner() && !relationship.partner()) continue;
                    UUID pokemonId = relationship.key().pokemonId();
                    String claimId = "automatic:" + definition.id() + ":" + definition.clockDimension() + ":" + day;
                    var result = rewards.grantOnce(
                            player,
                            pokemonId,
                            definition.rewardProfile(),
                            relationship.relationshipId(),
                            claimId
                    );
                    switch (result) {
                        case DELIVERED -> delivered++;
                        case DELIVERY_FAILED, UNKNOWN_PROFILE -> retryRequired = true;
                        default -> { }
                    }
                }

                if (!retryRequired) processedDays.put(processedKey, day);
                if (delivered > 0 && !definition.messageKey().isBlank()) {
                    player.sendMessage(messages.text(definition.messageKey(), Map.of("count", delivered)), false);
                }
            }
        }
    }

    private ServerWorld resolveWorld(String rawDimension) {
        Identifier id = Identifier.tryParse(rawDimension);
        if (id == null) return null;
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        return server.getWorld(key);
    }

    static boolean insideWindow(int tick, int start, int end) {
        if (start == end) return true;
        if (start < end) return tick >= start && tick < end;
        return tick >= start || tick < end;
    }

    private static List<Definition> parse(Path path) throws IOException {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root;
        try (InputStream input = Files.newInputStream(path)) {
            root = asMap(yaml.load(input), path.getFileName().toString());
        }
        Object rawDefinitions = root.get("automatic_rewards");
        if (!(rawDefinitions instanceof Map<?, ?> rawMap)) throw new IllegalArgumentException("Expected map at automatic_rewards");

        List<Definition> result = new ArrayList<>();
        for (var rawEntry : rawMap.entrySet()) {
            String id = String.valueOf(rawEntry.getKey());
            Map<String, Object> value = asMap(rawEntry.getValue(), "automatic reward " + id);
            String rewardProfile = string(value, "reward_profile");
            boolean requirePartner = bool(value, "require_partner", true);
            String clockDimension = string(value, "clock_dimension");
            int startTick = integer(value, "start_tick");
            int endTick = integer(value, "end_tick");
            int interval = integer(value, "evaluation_interval_ticks");
            String messageKey = string(value, "message_key");
            if (startTick < 0 || startTick >= DAY_TICKS || endTick < 0 || endTick >= DAY_TICKS) {
                throw new IllegalArgumentException("Automatic reward ticks must be within 0..23999: " + id);
            }
            if (interval < 1) throw new IllegalArgumentException("evaluation_interval_ticks must be >= 1: " + id);
            result.add(new Definition(id, rewardProfile, requirePartner, clockDimension, startTick, endTick, interval, messageKey));
        }
        return result;
    }

    private static Map<String, Object> asMap(Object value, String label) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected map at " + label);
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String string(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) throw new IllegalArgumentException("Missing key: " + key);
        return String.valueOf(value);
    }

    private static int integer(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(string(source, key));
    }

    private static boolean bool(Map<String, Object> source, String key, boolean fallback) {
        Object value = source.get(key);
        if (value == null) return fallback;
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    public record Definition(
            String id,
            String rewardProfile,
            boolean requirePartner,
            String clockDimension,
            int startTick,
            int endTick,
            int evaluationIntervalTicks,
            String messageKey
    ) { }

    public record ReloadResult(boolean success, String detail) { }
}
