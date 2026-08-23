package vn.svframe.mmoitemsfabric.runtime.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pure runtime port of MMOItems 6.10.1 CraftingStatus.CraftingQueue timing semantics. */
public final class CraftingQueueRuntime {
    private final List<QueueItem> crafts = new ArrayList<>();

    public List<QueueItem> crafts() { return List.copyOf(crafts); }

    public QueueItem add(String recipeId, long craftingTimeMillis, long now) {
        if (craftingTimeMillis < 0) throw new IllegalArgumentException("craftingTimeMillis < 0");
        long base = crafts.isEmpty() ? now : Math.max(now, crafts.get(crafts.size() - 1).completion);
        QueueItem item = new QueueItem(recipeId, craftingTimeMillis, now, base + craftingTimeMillis);
        crafts.add(item);
        return item;
    }

    public void remove(QueueItem item, long now) {
        int index = crafts.indexOf(item);
        if (index < 0) throw new IllegalArgumentException("Could not find item in queue");
        crafts.remove(index);
        long delay = Math.min(item.left(now), item.craftingTimeMillis);
        for (int i = index; i < crafts.size(); i++) crafts.get(i).removeDelay(delay);
    }

    public QueueItem getCraft(UUID uniqueId) {
        for (QueueItem item : crafts) if (item.uniqueId.equals(uniqueId)) return item;
        return null;
    }

    public List<Map<String, Object>> toRecords() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (QueueItem item : crafts) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("Recipe", item.recipeId);
            record.put("Start", item.start);
            record.put("Completion", item.completion);
            out.add(record);
        }
        return List.copyOf(out);
    }

    public void loadRecords(List<Map<String, Object>> records, RecipeTimeResolver resolver) {
        crafts.clear();
        for (Map<String, Object> record : records) {
            String recipe = Objects.toString(record.get("Recipe"));
            long start = ((Number) record.get("Start")).longValue();
            long completion = ((Number) record.get("Completion")).longValue();
            crafts.add(new QueueItem(recipe, resolver.craftingTimeMillis(recipe), start, completion));
        }
    }

    @FunctionalInterface
    public interface RecipeTimeResolver { long craftingTimeMillis(String recipeId); }

    public static final class QueueItem {
        private final String recipeId;
        private final long craftingTimeMillis;
        private final UUID uniqueId = UUID.randomUUID();
        private final long start;
        private long completion;

        private QueueItem(String recipeId, long craftingTimeMillis, long start, long completion) {
            this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
            this.craftingTimeMillis = craftingTimeMillis;
            this.start = start;
            this.completion = completion;
        }

        public String recipeId() { return recipeId; }
        public UUID uniqueId() { return uniqueId; }
        public long start() { return start; }
        public long completion() { return completion; }
        public boolean ready(long now) { return now >= completion; }
        public void removeDelay(long delay) { completion -= delay; }
        public long elapsed(long now) { return Math.max(craftingTimeMillis, now - start); }
        public long left(long now) { return Math.max(0L, completion - now); }
    }
}
