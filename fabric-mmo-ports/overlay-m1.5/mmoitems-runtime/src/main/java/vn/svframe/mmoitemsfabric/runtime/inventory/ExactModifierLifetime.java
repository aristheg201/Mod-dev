package vn.svframe.mmoitemsfabric.runtime.inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks the exact modifier instances contributed by each equipped item.
 * Removal is by runtime modifier identity, never by stat key.
 */
public final class ExactModifierLifetime {
    private final Map<UUID, List<Modifier>> byItem = new LinkedHashMap<>();

    public void register(UUID itemId, Collection<Modifier> modifiers) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(modifiers, "modifiers");
        if (byItem.containsKey(itemId)) {
            throw new IllegalStateException("Modifiers already registered for equipped item " + itemId);
        }
        byItem.put(itemId, List.copyOf(modifiers));
    }

    public List<Modifier> unregister(UUID itemId) {
        Objects.requireNonNull(itemId, "itemId");
        List<Modifier> removed = byItem.remove(itemId);
        return removed == null ? List.of() : removed;
    }

    public List<Modifier> snapshot() {
        List<Modifier> out = new ArrayList<>();
        byItem.values().forEach(out::addAll);
        return List.copyOf(out);
    }

    public int equippedItemCount() {
        return byItem.size();
    }

    public record Modifier(UUID id, String stat, double value) {
        public Modifier {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(stat, "stat");
        }
    }
}
