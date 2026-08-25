package vn.svframe.mythiclibfabric.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Optional cross-module RPG profile service without a hard MMOCore dependency. */
public final class RpgProfileRegistry {
    public record Snapshot(int level, String playerClass, Map<String, Double> attributes) {
        public Snapshot {
            level = Math.max(1, level);
            playerClass = Objects.requireNonNullElse(playerClass, "");
            attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
        }
    }

    @FunctionalInterface
    public interface Provider {
        Snapshot snapshot(UUID player);
    }

    private static final CopyOnWriteArrayList<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private RpgProfileRegistry() {}

    public static AutoCloseable register(Provider provider) {
        Objects.requireNonNull(provider, "provider");
        PROVIDERS.add(provider);
        return () -> PROVIDERS.remove(provider);
    }

    public static Optional<Snapshot> resolve(UUID player) {
        for (Provider provider : PROVIDERS) {
            Snapshot snapshot = provider.snapshot(player);
            if (snapshot != null) return Optional.of(snapshot);
        }
        return Optional.empty();
    }

    public static Snapshot mergeOrDefault(UUID player) {
        int level = 1;
        String playerClass = "";
        Map<String, Double> attributes = new LinkedHashMap<>();
        for (Provider provider : PROVIDERS) {
            Snapshot snapshot = provider.snapshot(player);
            if (snapshot == null) continue;
            level = Math.max(level, snapshot.level());
            if (playerClass.isEmpty() && !snapshot.playerClass().isEmpty()) playerClass = snapshot.playerClass();
            snapshot.attributes().forEach(attributes::putIfAbsent);
        }
        return new Snapshot(level, playerClass, attributes);
    }
}
