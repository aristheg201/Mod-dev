package vn.svframe.svrelationships.fabric.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import vn.svframe.svrelationships.gameplay.GameplayDefinitions;
import vn.svframe.svrelationships.gameplay.PartnerCapacityDefinition;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            validateRoutes(routes);
            current.set(new GameplayDefinitions(generation.incrementAndGet(), tracks, routes, capacity, rewards));
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
            result.put(entry.getKey(), new ProgressionTrackDefinition(entry.getKey(), longValue(value, "minimum"), longValue(value, "maximum"), ranks));
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
                states.put(state.getKey(), new RouteDefinition.State(state.getKey(), string(stateValue, "display_key"), stringList(stateValue, "transitions")));
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
                            string(reward, "id"),
                            integer(reward, "weight"),
                            string(reward, "reward_type"),
                            string(reward, "value"),
                            longValue(reward, "minimum_amount"),
                            longValue(reward, "maximum_amount")
                    ));
                }
            }
            result.put(entry.getKey(), new RewardProfileDefinition(entry.getKey(), string(value, "trigger_type"), string(value, "period"), string(value, "scope"), entries));
        }
        return result;
    }

    private void validateRoutes(Map<String, RouteDefinition> routes) {
        for (RouteDefinition route : routes.values()) {
            for (RouteDefinition.State state : route.states().values()) {
                for (String transition : state.transitions()) {
                    if (!route.states().containsKey(transition)) {
                        throw new IllegalArgumentException("Route " + route.id() + " references missing state " + transition);
                    }
                }
            }
        }
    }

    private Map<String, Object> load(Path path) throws IOException {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (InputStream input = Files.newInputStream(path)) {
            Object loaded = yaml.load(input);
            return mapValue(loaded, path.getFileName().toString());
        }
    }

    private void installDefault(String resource, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        try (InputStream input = GameplayDefinitionService.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing bundled resource: " + resource);
            }
            Files.copy(input, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static Map<String, Object> map(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return mapValue(value, key);
    }

    private static Map<String, Object> mapValue(Object value, String label) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Expected map at " + label);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String string(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing key: " + key);
        }
        return String.valueOf(value);
    }

    private static int integer(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number number ? number.intValue() : Integer.parseInt(string(source, key));
    }

    private static long longValue(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Number number ? number.longValue() : Long.parseLong(string(source, key));
    }

    private static List<String> stringList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected list at " + key);
        }
        return list.stream().map(String::valueOf).toList();
    }

    public record ReloadResult(boolean success, String detail) {
    }
}
