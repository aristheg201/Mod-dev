package io.github.aristheg201.cobblemonworld.faction;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IslandSpawnPoolRegistry {
    public static final IslandSpawnPoolRegistry INSTANCE = new IslandSpawnPoolRegistry();
    private static final Gson GSON = new Gson();
    private Map<String, Pool> pools = new LinkedHashMap<>();

    private IslandSpawnPoolRegistry() {}

    public void load() {
        String path = "data/cobblemonworld/faction/island_spawn_pools.json";
        try (var stream = IslandSpawnPoolRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            Root root = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Root.class);
            pools = root == null || root.pools == null ? new LinkedHashMap<>() : root.pools;
            CobblemonWorldMod.LOGGER.info("Loaded {} faction island spawn affinities.", pools.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load faction island spawn pools", e);
        }
    }

    public Pool get(String affinity) {
        return pools.getOrDefault(affinity, new Pool(List.of("pikachu"), List.of(), List.of()));
    }

    public record Pool(List<String> common, List<String> rare, List<String> legendary) {
        public Pool {
            common = common == null ? Collections.emptyList() : common;
            rare = rare == null ? Collections.emptyList() : rare;
            legendary = legendary == null ? Collections.emptyList() : legendary;
        }
    }

    private static final class Root {
        Map<String, Pool> pools;
    }
}
