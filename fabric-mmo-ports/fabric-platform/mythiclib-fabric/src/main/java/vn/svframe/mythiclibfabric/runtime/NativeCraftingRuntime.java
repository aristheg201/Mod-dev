package vn.svframe.mythiclibfabric.runtime;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Native station-independent MythicLib crafting registry/runtime. */
public final class NativeCraftingRuntime {
    public enum Station { WORKBENCH, FURNACE, SMITHING, BREWING, CUSTOM }
    private static final Map<Key, List<Recipe>> RECIPES = new ConcurrentHashMap<>();
    private NativeCraftingRuntime() {}

    public static void clear() { RECIPES.clear(); }
    public static void register(Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        Key key = new Key(recipe.station(), normalizeKey(recipe.customStationKey()));
        RECIPES.compute(key, (ignored, old) -> {
            List<Recipe> copy = old == null ? new ArrayList<>() : new ArrayList<>(old);
            copy.removeIf(existing -> existing.id().equals(recipe.id()));
            copy.add(recipe);
            copy.sort(Comparator.comparingInt(Recipe::priority).reversed().thenComparing(Recipe::id));
            return List.copyOf(copy);
        });
    }
    public static boolean unregister(Station station, String customStationKey, String id) {
        Key key = new Key(station, normalizeKey(customStationKey));
        final boolean[] removed = {false};
        RECIPES.computeIfPresent(key, (ignored, old) -> {
            List<Recipe> copy = new ArrayList<>(old);
            removed[0] = copy.removeIf(recipe -> recipe.id().equals(id));
            return copy.isEmpty() ? null : List.copyOf(copy);
        });
        return removed[0];
    }
    public static List<Recipe> recipes(Station station, String customStationKey) {
        return RECIPES.getOrDefault(new Key(station, normalizeKey(customStationKey)), List.of());
    }
    public static Optional<Match> match(Station station, String customStationKey, SlotAccess slots) {
        Objects.requireNonNull(slots, "slots");
        for (Recipe recipe : recipes(station, customStationKey)) if (recipe.matches(slots)) return Optional.of(new Match(recipe, slots));
        return Optional.empty();
    }
    public static ItemStack result(Station station, String customStationKey, SlotAccess slots) {
        return match(station, customStationKey, slots).map(value -> value.recipe().createResult()).orElse(ItemStack.EMPTY);
    }
    public static boolean craft(Station station, String customStationKey, SlotAccess slots, int times) {
        if (times <= 0) return false;
        boolean crafted = false;
        for (int i = 0; i < times; i++) {
            Optional<Match> match = match(station, customStationKey, slots);
            if (match.isEmpty()) break;
            match.get().consume();
            crafted = true;
        }
        return crafted;
    }

    public interface SlotAccess {
        ItemStack get(int logicalSlot);
        void set(int logicalSlot, ItemStack stack);
        void markDirty();
    }
    public record Match(Recipe recipe, SlotAccess slots) { public void consume() { recipe.consume(slots); } }

    public static final class Recipe {
        private final String id;
        private final Station station;
        private final String customStationKey;
        private final Map<Integer, Input> inputs;
        private final Predicate<SlotAccess> extraCondition;
        private final Supplier<ItemStack> result;
        private final int priority;
        private final boolean requireEmptyUnspecifiedSlots;
        private final int[] relevantSlots;
        public Recipe(String id, Station station, String customStationKey, Map<Integer, Input> inputs,
                      Predicate<SlotAccess> extraCondition, Supplier<ItemStack> result, int priority,
                      boolean requireEmptyUnspecifiedSlots, int[] relevantSlots) {
            this.id = Objects.requireNonNull(id, "id");
            this.station = Objects.requireNonNull(station, "station");
            this.customStationKey = normalizeKey(customStationKey);
            this.inputs = Map.copyOf(new LinkedHashMap<>(inputs));
            this.extraCondition = extraCondition == null ? ignored -> true : extraCondition;
            this.result = Objects.requireNonNull(result, "result");
            this.priority = priority;
            this.requireEmptyUnspecifiedSlots = requireEmptyUnspecifiedSlots;
            this.relevantSlots = relevantSlots == null ? inputs.keySet().stream().mapToInt(Integer::intValue).toArray() : relevantSlots.clone();
        }
        public String id() { return id; }
        public Station station() { return station; }
        public String customStationKey() { return customStationKey; }
        public int priority() { return priority; }
        public boolean matches(SlotAccess slots) {
            for (Map.Entry<Integer, Input> entry : inputs.entrySet()) if (!entry.getValue().matches(slots.get(entry.getKey()))) return false;
            if (requireEmptyUnspecifiedSlots) for (int logicalSlot : relevantSlots) {
                if (inputs.containsKey(logicalSlot)) continue;
                ItemStack stack = slots.get(logicalSlot);
                if (stack != null && !stack.isEmpty()) return false;
            }
            return extraCondition.test(slots);
        }
        public ItemStack createResult() {
            ItemStack stack = result.get();
            return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        private void consume(SlotAccess slots) {
            for (Map.Entry<Integer, Input> entry : inputs.entrySet()) {
                int slot = entry.getKey();
                ItemStack current = slots.get(slot);
                if (current == null || current.isEmpty()) continue;
                ItemStack remainder = current.copy();
                remainder.decrement(entry.getValue().amount());
                slots.set(slot, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
            }
            slots.markDirty();
        }
    }

    public static final class Input {
        private final Predicate<ItemStack> predicate;
        private final int amount;
        private Input(Predicate<ItemStack> predicate, int amount) {
            this.predicate = Objects.requireNonNull(predicate, "predicate");
            if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
            this.amount = amount;
        }
        public static Input ingredient(Ingredient ingredient, int amount) {
            Objects.requireNonNull(ingredient, "ingredient");
            return new Input(stack -> stack != null && !stack.isEmpty() && stack.getCount() >= amount && ingredient.test(stack), amount);
        }
        public static Input exact(ItemStack template, int amount) {
            Objects.requireNonNull(template, "template");
            ItemStack normalized = template.copyWithCount(1);
            return new Input(stack -> stack != null && !stack.isEmpty() && stack.getCount() >= amount && ItemStack.areItemsAndComponentsEqual(normalized, stack), amount);
        }
        public static Input itemId(String itemId, int amount) {
            return new Input(stack -> stack != null && !stack.isEmpty() && stack.getCount() >= amount && Registries.ITEM.getId(stack.getItem()).toString().equals(itemId), amount);
        }
        public static Input predicate(Predicate<ItemStack> predicate, int amount) {
            return new Input(stack -> stack != null && !stack.isEmpty() && stack.getCount() >= amount && predicate.test(stack), amount);
        }
        public boolean matches(ItemStack stack) { return predicate.test(stack); }
        public int amount() { return amount; }
    }
    private record Key(Station station, String customStationKey) {}
    private static String normalizeKey(String value) { return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT); }
}
