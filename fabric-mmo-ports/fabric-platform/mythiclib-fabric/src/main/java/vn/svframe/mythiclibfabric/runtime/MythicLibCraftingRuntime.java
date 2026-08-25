package vn.svframe.mythiclibfabric.runtime;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Native station-independent MythicLib crafting registry/runtime. */
public final class MythicLibCraftingRuntime {
    public enum Station { WORKBENCH, FURNACE, SMITHING, BREWING, CUSTOM }
    private static final Map<Key, List<Recipe>> RECIPES = new ConcurrentHashMap<>();
    private MythicLibCraftingRuntime() {}

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
        return match(station, customStationKey, slots, ignored -> true);
    }
    public static Optional<Match> match(Station station, String customStationKey, SlotAccess slots, Predicate<String> permissionCheck) {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(permissionCheck, "permissionCheck");
        for (Recipe recipe : recipes(station, customStationKey)) {
            if (!recipe.checkPermissions(permissionCheck)) continue;
            Optional<ConsumptionPlan> plan = recipe.plan(slots);
            if (plan.isPresent()) return Optional.of(new Match(recipe, slots, plan.get()));
        }
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
    public record Match(Recipe recipe, SlotAccess slots, ConsumptionPlan plan) {
        public void consume() { plan.consume(slots); }
    }

    public static final class ConsumptionPlan {
        private final Map<Integer, Integer> amounts;
        private ConsumptionPlan(Map<Integer, Integer> amounts) { this.amounts = Map.copyOf(amounts); }
        public Map<Integer, Integer> amounts() { return amounts; }
        private void consume(SlotAccess slots) {
            for (Map.Entry<Integer, Integer> entry : amounts.entrySet()) {
                ItemStack current = slots.get(entry.getKey());
                if (current == null || current.isEmpty()) continue;
                ItemStack remainder = current.copy();
                remainder.decrement(entry.getValue());
                slots.set(entry.getKey(), remainder.isEmpty() ? ItemStack.EMPTY : remainder);
            }
            slots.markDirty();
        }
    }

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
        private final Function<SlotAccess, Optional<ConsumptionPlan>> customMatcher;
        private final CopyOnWriteArrayList<String> requiredPermissions = new CopyOnWriteArrayList<>();

        public Recipe(String id, Station station, String customStationKey, Map<Integer, Input> inputs,
                      Predicate<SlotAccess> extraCondition, Supplier<ItemStack> result, int priority,
                      boolean requireEmptyUnspecifiedSlots, int[] relevantSlots) {
            this(id, station, customStationKey, inputs, extraCondition, result, priority,
                    requireEmptyUnspecifiedSlots, relevantSlots, null);
        }

        private Recipe(String id, Station station, String customStationKey, Map<Integer, Input> inputs,
                       Predicate<SlotAccess> extraCondition, Supplier<ItemStack> result, int priority,
                       boolean requireEmptyUnspecifiedSlots, int[] relevantSlots,
                       Function<SlotAccess, Optional<ConsumptionPlan>> customMatcher) {
            this.id = Objects.requireNonNull(id, "id");
            this.station = Objects.requireNonNull(station, "station");
            this.customStationKey = normalizeKey(customStationKey);
            this.inputs = Map.copyOf(new LinkedHashMap<>(inputs));
            this.extraCondition = extraCondition == null ? ignored -> true : extraCondition;
            this.result = Objects.requireNonNull(result, "result");
            this.priority = priority;
            this.requireEmptyUnspecifiedSlots = requireEmptyUnspecifiedSlots;
            this.relevantSlots = relevantSlots == null ? inputs.keySet().stream().mapToInt(Integer::intValue).toArray() : relevantSlots.clone();
            this.customMatcher = customMatcher;
        }

        public static Recipe shaped(String id, Station station, String customStationKey,
                                    int gridWidth, int gridHeight, int recipeWidth, int recipeHeight,
                                    List<Input> rowMajor, Predicate<SlotAccess> extraCondition,
                                    Supplier<ItemStack> result, int priority) {
            if (gridWidth <= 0 || gridHeight <= 0 || recipeWidth <= 0 || recipeHeight <= 0
                    || recipeWidth > gridWidth || recipeHeight > gridHeight) throw new IllegalArgumentException("Invalid shaped recipe dimensions");
            if (rowMajor.size() != recipeWidth * recipeHeight) throw new IllegalArgumentException("rowMajor size does not match recipe dimensions");
            List<Input> cells = Collections.unmodifiableList(new ArrayList<>(rowMajor));
            int[] relevant = new int[gridWidth * gridHeight];
            for (int i = 0; i < relevant.length; i++) relevant[i] = i;
            Function<SlotAccess, Optional<ConsumptionPlan>> matcher = slots -> {
                for (int oy = 0; oy <= gridHeight - recipeHeight; oy++) for (int ox = 0; ox <= gridWidth - recipeWidth; ox++) {
                    Map<Integer, Integer> consume = new LinkedHashMap<>();
                    boolean ok = true;
                    for (int y = 0; y < gridHeight && ok; y++) for (int x = 0; x < gridWidth; x++) {
                        int slot = y * gridWidth + x;
                        int rx = x - ox, ry = y - oy;
                        Input expected = rx >= 0 && ry >= 0 && rx < recipeWidth && ry < recipeHeight ? cells.get(ry * recipeWidth + rx) : null;
                        ItemStack stack = slots.get(slot);
                        if (expected == null) { if (stack != null && !stack.isEmpty()) { ok = false; break; } }
                        else if (!expected.matches(stack)) { ok = false; break; }
                        else consume.put(slot, expected.amount());
                    }
                    if (ok && (extraCondition == null || extraCondition.test(slots))) return Optional.of(new ConsumptionPlan(consume));
                }
                return Optional.empty();
            };
            return new Recipe(id, station, customStationKey, Map.of(), extraCondition, result, priority, true, relevant, matcher);
        }

        public static Recipe shapeless(String id, Station station, String customStationKey, int[] relevantSlots,
                                       List<Input> requirements, Predicate<SlotAccess> extraCondition,
                                       Supplier<ItemStack> result, int priority) {
            int[] slotsCopy = relevantSlots.clone();
            List<Input> needs = List.copyOf(requirements);
            Function<SlotAccess, Optional<ConsumptionPlan>> matcher = slots -> {
                Map<Integer, Integer> remaining = new HashMap<>();
                for (int raw : slotsCopy) { ItemStack stack = slots.get(raw); remaining.put(raw, stack == null ? 0 : stack.getCount()); }
                Map<Integer, Integer> consume = new LinkedHashMap<>();
                if (!assignShapeless(0, needs, slotsCopy, slots, remaining, consume)) return Optional.empty();
                Set<Integer> used = new HashSet<>(consume.keySet());
                for (int raw : slotsCopy) {
                    ItemStack stack = slots.get(raw);
                    if (stack != null && !stack.isEmpty() && !used.contains(raw)) return Optional.empty();
                }
                if (extraCondition != null && !extraCondition.test(slots)) return Optional.empty();
                return Optional.of(new ConsumptionPlan(consume));
            };
            return new Recipe(id, station, customStationKey, Map.of(), extraCondition, result, priority, true, slotsCopy, matcher);
        }

        private static boolean assignShapeless(int index, List<Input> needs, int[] slots, SlotAccess access,
                                               Map<Integer, Integer> remaining, Map<Integer, Integer> consume) {
            if (index >= needs.size()) return true;
            Input need = needs.get(index);
            for (int slot : slots) {
                int available = remaining.getOrDefault(slot, 0);
                if (available < need.amount()) continue;
                ItemStack original = access.get(slot);
                if (original == null || original.isEmpty()) continue;
                ItemStack probe = original.copyWithCount(available);
                if (!need.matches(probe)) continue;
                remaining.put(slot, available - need.amount());
                consume.merge(slot, need.amount(), Integer::sum);
                if (assignShapeless(index + 1, needs, slots, access, remaining, consume)) return true;
                int reverted = consume.get(slot) - need.amount();
                if (reverted == 0) consume.remove(slot); else consume.put(slot, reverted);
                remaining.put(slot, available);
            }
            return false;
        }

        public String id() { return id; }
        public Station station() { return station; }
        public String customStationKey() { return customStationKey; }
        public int priority() { return priority; }
        public List<String> requiredPermissions() { return List.copyOf(requiredPermissions); }
        public Recipe addRequiredPermission(String permission) { if (permission != null && !permission.isBlank()) requiredPermissions.addIfAbsent(permission.trim()); return this; }
        public Recipe clearRequiredPermissions() { requiredPermissions.clear(); return this; }
        public boolean checkPermissions(Predicate<String> permissionCheck) { for (String permission : requiredPermissions) if (!permissionCheck.test(permission)) return false; return true; }
        public boolean matches(SlotAccess slots) { return plan(slots).isPresent(); }
        private Optional<ConsumptionPlan> plan(SlotAccess slots) {
            if (customMatcher != null) return customMatcher.apply(slots);
            Map<Integer, Integer> consume = new LinkedHashMap<>();
            for (Map.Entry<Integer, Input> entry : inputs.entrySet()) {
                if (!entry.getValue().matches(slots.get(entry.getKey()))) return Optional.empty();
                consume.put(entry.getKey(), entry.getValue().amount());
            }
            if (requireEmptyUnspecifiedSlots) for (int logicalSlot : relevantSlots) {
                if (inputs.containsKey(logicalSlot)) continue;
                ItemStack stack = slots.get(logicalSlot);
                if (stack != null && !stack.isEmpty()) return Optional.empty();
            }
            return extraCondition.test(slots) ? Optional.of(new ConsumptionPlan(consume)) : Optional.empty();
        }
        public ItemStack createResult() {
            ItemStack stack = result.get();
            return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
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
