package vn.svframe.svrelationships.fabric.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.svframe.svrelationships.family.DaycareDefinition;
import vn.svframe.svrelationships.family.InheritanceDefinition;
import vn.svframe.svrelationships.gameplay.GameplayDefinitions;
import vn.svframe.svrelationships.gameplay.GiftDefinition;
import vn.svframe.svrelationships.gameplay.InteractionDefinition;
import vn.svframe.svrelationships.gameplay.PartnerCapacityDefinition;
import vn.svframe.svrelationships.gameplay.PersonalityDefinition;
import vn.svframe.svrelationships.gameplay.ProgressionTrackDefinition;
import vn.svframe.svrelationships.gameplay.RewardProfileDefinition;
import vn.svframe.svrelationships.gameplay.RouteDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class GameplayDefinitionService {
    private final Path root;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<GameplayDefinitions> current = new AtomicReference<>();

    public GameplayDefinitionService(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    public void initialize() {
        try {
            installDefault("/defaults/progression.yml", root.resolve("progression.yml"));
            installDefault("/defaults/routes.yml", root.resolve("routes.yml"));
            installDefault("/defaults/ranks.yml", root.resolve("ranks.yml"));
            installDefault("/defaults/rewards.yml", root.resolve("rewards.yml"));
            installDefault("/defaults/interactions.yml", root.resolve("interactions.yml"));
            installDefault("/defaults/gifts.yml", root.resolve("gifts.yml"));
            installDefault("/defaults/personalities.yml", root.resolve("personalities.yml"));
            installDefault("/defaults/daycare.yml", root.resolve("daycare.yml"));
            installDefault("/defaults/inheritance.yml", root.resolve("inheritance.yml"));
            installDirectoryDefault("/defaults/gui/main.yml", root.resolve("gui/main.yml"));
            installDirectoryDefault("/defaults/gui/relationship.yml", root.resolve("gui/relationship.yml"));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to install gameplay definitions", exception);
        }
        ReloadResult result = reload();
        if (!result.success()) {
            throw new IllegalStateException("Unable to load gameplay definitions: " + result.detail());
        }
    }

    public GameplayDefinitions snapshot() {
        return Objects.requireNonNull(current.get(), "gameplay definitions not initialized");
    }

    public ReloadResult reload() {
        try {
            var tracks = parseTracks(load(root.resolve("progression.yml")));
            var routes = parseRoutes(load(root.resolve("routes.yml")));
            var capacity = parseCapacity(load(root.resolve("ranks.yml")));
            var rewards = parseRewards(load(root.resolve("rewards.yml")));
            var interactions = parseInteractions(load(root.resolve("interactions.yml")));
            var gifts = parseGifts(load(root.resolve("gifts.yml")));
            var personalities = parsePersonalities(load(root.resolve("personalities.yml")));
            var daycare = parseDaycare(load(root.resolve("daycare.yml")));
            var inheritance = parseInheritance(load(root.resolve("inheritance.yml")));
            validateRoutes(routes);
            validateReferences(interactions, gifts, daycare, inheritance, tracks, routes, rewards);
            current.set(new GameplayDefinitions(
                    generation.incrementAndGet(), tracks, routes, capacity, rewards,
                    interactions, gifts, personalities, daycare, inheritance
            ));
            return new ReloadResult(true, "");
        } catch (Exception exception) {
            return new ReloadResult(false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private Map<String, ProgressionTrackDefinition> parseTracks(Map<String, Object> rootMap) {
        Map<String, ProgressionTrackDefinition> result = new LinkedHashMap<>();
        for (var entry : map(rootMap, "tracks").entrySet()) {
            Map<String, Object> value = mapValue(entry.getValue(), "track " + entry.getKey());
            List<ProgressionTrackDefinition.Rank> ranks = new ArrayList<>();
            for (var rank : map(value, "ranks").entrySet()) {
                Map<String, Object> rankValue = mapValue(rank.getValue(), "rank " + rank.getKey());
                ranks.add(new ProgressionTrackDefinition.Rank(rank.getKey(), longValue(rankValue, "minimum"), string(rankValue, "display_key")));
            }
            ranks.sort(Comparator.comparingLong(ProgressionTrackDefinition.Rank::minimumValue));
            long minimum = longValue(value, "minimum");
            long maximum = longValue(value, "maximum");
            if (maximum < minimum) throw new IllegalArgumentException("Track maximum below minimum: " + entry.getKey());
            result.put(entry.getKey(), new ProgressionTrackDefinition(entry.getKey(), minimum, maximum, ranks));
        }
        return result;
    }

    private Map<String, RouteDefinition> parseRoutes(Map<String, Object> rootMap) {
        Map<String, RouteDefinition> result = new LinkedHashMap<>();
        for (var entry : map(rootMap, "routes").entrySet()) {
            Map<String, Object> value = mapValue(entry.getValue(), "route " + entry.getKey());
            Map<String, RouteDefinition.State> states = new LinkedHashMap<>();
            for (var state : map(value, "states").entrySet()) {
                Map<String, Object> stateValue = mapValue(state.getValue(), "state " + state.getKey());
                states.put(state.getKey(), new RouteDefinition.State(
                        state.getKey(), string(stateValue, "display_key"), stringList(stateValue, "transitions")
                ));
            }
            result.put(entry.getKey(), new RouteDefinition(entry.getKey(), string(value, "initial_state"), states));
        }
        return result;
    }

    private PartnerCapacityDefinition parseCapacity(Map<String, Object> rootMap) {
        Map<String, Object> value = map(rootMap, "partner_capacity");
        List<PartnerCapacityDefinition.Rule> rules = new ArrayList<>();
        Object rawRules = value.get("rules");
        if (rawRules instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> rule = mapValue(item, "partner capacity rule");
                rules.add(new PartnerCapacityDefinition.Rule(string(rule, "permission"), integer(rule, "capacity")));
            }
        }
        return new PartnerCapacityDefinition(integer(value, "fallback"), string(value, "downgrade_policy"), rules);
    }

    private Map<String, RewardProfileDefinition> parseRewards(Map<String, Object> rootMap) {
        Map<String, RewardProfileDefinition> result = new LinkedHashMap<>();
        for (var entry : map(rootMap, "profiles").entrySet()) {
            Map<String, Object> value = mapValue(entry.getValue(), "reward profile " + entry.getKey());
            List<RewardProfileDefinition.Entry> entries = new ArrayList<>();
            Object rawEntries = value.get("entries");
            if (rawEntries instanceof List<?> list) {
                for (Object item : list) {
                    Map<String, Object> reward = mapValue(item, "reward entry");
                    entries.add(new RewardProfileDefinition.Entry(
                            string(reward, "id"), integer(reward, "weight"), string(reward, "reward_type"),
                            string(reward, "value"), longValue(reward, "minimum_amount"), longValue(reward, "maximum_amount")
                    ));
                }
            }
            result.put(entry.getKey(), new RewardProfileDefinition(
                    entry.getKey(), string(value, "trigger_type"), string(value, "period"), string(value, "scope"), entries
            ));
        }
        return result;
    }

    private Map<String, InteractionDefinition> parseInteractions(Map<String, Object> rootMap) {
        Map<String, InteractionDefinition> result = new LinkedHashMap<>();
        for (var entry : map(rootMap, "interactions").entrySet()) {
            Map<String, Object> value = mapValue(entry.getValue(), "interaction " + entry.getKey());
            result.put(entry.getKey(), new InteractionDefinition(
                    entry.getKey(), durationMillis(string(value, "cooldown")), longMap(value, "progression"),
                    optionalString(value, "required_route", ""), optionalString(value, "required_state", ""),
                    string(value, "message_key")
            ));
        }
        return result;
    }

    private Map<String, GiftDefinition> parseGifts(Map<String, Object> rootMap) {
        Map<String, GiftDefinition> result = new LinkedHashMap<>();
        for (var entry : map(rootMap, "gifts").entrySet()) {
            Map<String, Object> value = mapValue(entry.getValue(), "gift " + entry.getKey());
            result.put(entry.getKey(), new GiftDefinition(
                    entry.getKey(), string(value, "item"), durationMillis(string(value, "cooldown")),
                    integer(value, "consume_amount"), longMap(value, "progression"), string(value, "message_key")
            ));
        }
        return result;
    }

    private Map<String, PersonalityDefinition> parsePersonalities(Map<String, Object> rootMap) {
        Map<String, PersonalityDefinition> result = new LinkedHashMap<>();
        for (var entry : map(rootMap, "personalities").entrySet()) {
            Map<String, Object> value = mapValue(entry.getValue(), "personality " + entry.getKey());
            result.put(entry.getKey(), new PersonalityDefinition(
                    entry.getKey(), new LinkedHashSet<>(stringList(value, "tags")),
                    doubleMap(value, "progression_multipliers"), doubleMap(value, "reward_weight_multipliers")
            ));
        }
        return result;
    }

    private Map<String, DaycareDefinition> parseDaycare(Map<String, Object> rootMap) {
        Map<String, DaycareDefinition> result = new LinkedHashMap<>();
        for (var entry : map(rootMap, "daycare").entrySet()) {
            Map<String, Object> value = mapValue(entry.getValue(), "daycare " + entry.getKey());
            result.put(entry.getKey(), new DaycareDefinition(
                    entry.getKey(), string(value, "mode"), stringList(value, "participant_roles"),
                    durationMillis(string(value, "duration")), string(value, "inheritance_profile"),
                    optionalString(value, "reward_profile", ""), optionalString(value, "economy_provider", ""),
                    optionalString(value, "currency", ""), longValue(value, "cost")
            ));
        }
        return result;
    }

    private Map<String, InheritanceDefinition> parseInheritance(Map<String, Object> rootMap) {
        Map<String, InheritanceDefinition> result = new LinkedHashMap<>();
        for (var entry : map(rootMap, "inheritance").entrySet()) {
            Map<String, Object> value = mapValue(entry.getValue(), "inheritance " + entry.getKey());
            result.put(entry.getKey(), new InheritanceDefinition(
                    entry.getKey(), integer(value, "guaranteed_iv_count"), doubleValue(value, "nature_chance"),
                    doubleValue(value, "ability_chance"), bool(value, "inherit_moves"),
                    string(value, "aspect_mode"), string(value, "personality_mode")
            ));
        }
        return result;
    }

    private void validateRoutes(Map<String, RouteDefinition> routes) {
        for (RouteDefinition route : routes.values()) {
            if (!route.states().containsKey(route.initialState())) {
                throw new IllegalArgumentException("Route " + route.id() + " has missing initial state " + route.initialState());
            }
            for (RouteDefinition.State state : route.states().values()) {
                for (String transition : state.transitions()) {
                    if (!route.states().containsKey(transition)) {
                        throw new IllegalArgumentException("Route " + route.id() + " references missing state " + transition);
                    }
                }
            }
        }
    }

    private void validateReferences(
            Map<String, InteractionDefinition> interactions,
            Map<String, GiftDefinition> gifts,
            Map<String, DaycareDefinition> daycare,
            Map<String, InheritanceDefinition> inheritance,
            Map<String, ProgressionTrackDefinition> tracks,
            Map<String, RouteDefinition> routes,
            Map<String, RewardProfileDefinition> rewards
    ) {
        interactions.values().forEach(definition -> {
            definition.progressionDeltas().keySet().forEach(track -> requireKey(tracks, track, "interaction track"));
            if (!definition.requiredRoute().isBlank()) {
                RouteDefinition route = requireKey(routes, definition.requiredRoute(), "interaction route");
                if (!definition.requiredState().isBlank() && !route.states().containsKey(definition.requiredState())) {
                    throw new IllegalArgumentException("Unknown interaction route state: " + definition.requiredState());
                }
            }
        });
        gifts.values().forEach(definition -> definition.progressionDeltas().keySet().forEach(track -> requireKey(tracks, track, "gift track")));
        daycare.values().forEach(definition -> {
            requireKey(inheritance, definition.inheritanceProfile(), "daycare inheritance profile");
            if (!definition.rewardProfile().isBlank()) requireKey(rewards, definition.rewardProfile(), "daycare reward profile");
        });
    }

    private Map<String, Object> load(Path path) throws IOException {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (InputStream input = Files.newInputStream(path)) {
            Object loaded = yaml.load(input);
            return mapValue(loaded, path.getFileName().toString());
        }
    }

    private void installDefault(String resource, Path target) throws IOException {
        installDirectoryDefault(resource, target);
    }

    private void installDirectoryDefault(String resource, Path target) throws IOException {
        if (Files.exists(target)) return;
        Files.createDirectories(target.getParent());
        try (InputStream input = GameplayDefinitionService.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing bundled resource: " + resource);
            Files.copy(input, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static <T> T requireKey(Map<String, T> map, String key, String label) {
        T value = map.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown " + label + ": " + key);
        return value;
    }

    private static Map<String, Object> map(Map<String, Object> source, String key) {
        return mapValue(source.get(key), key);
    }

    private static Map<String, Object> mapValue(Object value, String label) {
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

    private static String optionalString(Map<String, Object> source, String key, String fallback) {
        Object value = source.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(string(source, key));
    }

    private static long longValue(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number number ? number.longValue() : Long.parseLong(string(source, key));
    }

    private static double doubleValue(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(string(source, key));
    }

    private static boolean bool(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Boolean b ? b : Boolean.parseBoolean(string(source, key));
    }

    private static List<String> stringList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Expected list at " + key);
        return list.stream().map(String::valueOf).toList();
    }

    private static Map<String, Long> longMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected map at " + key);
        Map<String, Long> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v))));
        return result;
    }

    private static Map<String, Double> doubleMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Expected map at " + key);
        Map<String, Double> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), v instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(v))));
        return result;
    }

    public static long durationMillis(String value) {
        String input = value.trim().toLowerCase();
        if (input.isEmpty()) throw new IllegalArgumentException("Empty duration");
        long multiplier;
        String number;
        if (input.endsWith("ms")) { multiplier = 1L; number = input.substring(0, input.length() - 2); }
        else if (input.endsWith("s")) { multiplier = 1_000L; number = input.substring(0, input.length() - 1); }
        else if (input.endsWith("m")) { multiplier = 60_000L; number = input.substring(0, input.length() - 1); }
        else if (input.endsWith("h")) { multiplier = 3_600_000L; number = input.substring(0, input.length() - 1); }
        else if (input.endsWith("d")) { multiplier = 86_400_000L; number = input.substring(0, input.length() - 1); }
        else { multiplier = 1L; number = input; }
        return Math.multiplyExact(Long.parseLong(number), multiplier);
    }

    public record ReloadResult(boolean success, String detail) {}
}
