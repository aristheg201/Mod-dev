package vn.svframe.mythiclibfabric.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Native Fabric implementation of MythicLib 1.7.1 stat-instance semantics.
 *
 * <p>The calculation order intentionally matches the original runtime:
 * flat modifiers mutate the base, additive multipliers are accumulated, and
 * relative multipliers are multiplied together. The result is therefore
 * {@code (base + flat) * additiveMultiplier * relativeMultiplier}.</p>
 */
public final class NativeStatEngine {
    public enum ModifierType {
        RELATIVE,
        ADDITIVE_MULTIPLIER,
        FLAT
    }

    public enum EquipmentSlot {
        ARMOR(false, false),
        HEAD(true, false),
        CHEST(true, false),
        LEGS(true, false),
        FEET(true, false),
        ACCESSORY(false, false),
        INVENTORY(false, false),
        MAIN_HAND(false, true),
        OFF_HAND(false, true),
        OTHER(false, false);

        private final boolean body;
        private final boolean hand;

        EquipmentSlot(boolean body, boolean hand) {
            this.body = body;
            this.hand = hand;
        }

        public boolean isBody() {
            return body;
        }

        public boolean isHand() {
            return hand;
        }

        public boolean isCompatible(ModifierSource source, EquipmentSlot modifierSlot) {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(modifierSlot, "modifierSlot");
            if (!isHand()) throw new IllegalStateException("Instance called must be a hand equipment slot");
            if (modifierSlot == OTHER) return true;
            return switch (source) {
                case VOID -> false;
                case OTHER -> true;
                case RANGED_WEAPON, MELEE_WEAPON -> modifierSlot == this;
                case OFFHAND_ITEM -> modifierSlot == OFF_HAND;
                case MAINHAND_ITEM -> modifierSlot == MAIN_HAND;
                case HAND_ITEM -> modifierSlot.isHand();
                case ARMOR -> modifierSlot.isBody();
                case ACCESSORY -> modifierSlot == ACCESSORY;
                case ORNAMENT -> modifierSlot == INVENTORY;
            };
        }
    }

    public enum ModifierSource {
        MELEE_WEAPON,
        RANGED_WEAPON,
        OFFHAND_ITEM,
        MAINHAND_ITEM,
        HAND_ITEM,
        ARMOR,
        ACCESSORY,
        ORNAMENT,
        OTHER,
        VOID
    }

    public record Modifier(
            UUID id,
            String key,
            double value,
            ModifierType type,
            EquipmentSlot slot,
            ModifierSource source,
            long expiresAtTick
    ) {
        public Modifier {
            Objects.requireNonNull(id, "id");
            key = key == null ? "" : key;
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Modifier value must be finite");
            Objects.requireNonNull(type, "type");
            slot = slot == null ? EquipmentSlot.OTHER : slot;
            source = source == null ? ModifierSource.OTHER : source;
        }

        public Modifier(UUID id, String key, double value, ModifierType type,
                        EquipmentSlot slot, ModifierSource source) {
            this(id, key, value, type, slot, source, Long.MAX_VALUE);
        }

        public boolean temporary() {
            return expiresAtTick != Long.MAX_VALUE;
        }

        public boolean expired(long tick) {
            return temporary() && tick >= expiresAtTick;
        }

        public Modifier add(double amount) {
            return new Modifier(id, key, value + amount, type, slot, source, expiresAtTick);
        }

        public Modifier multiply(double multiplier) {
            return new Modifier(id, key, value * multiplier, type, slot, source, expiresAtTick);
        }
    }

    public static final class StatInstance {
        private final String stat;
        private final Map<UUID, Modifier> modifiers = new LinkedHashMap<>();
        private double base;
        private double defaultBase;

        private StatInstance(String stat) {
            this.stat = stat;
        }

        public String stat() {
            return stat;
        }

        public synchronized double base() {
            return base;
        }

        public synchronized double defaultBase() {
            return defaultBase;
        }

        public synchronized void setBase(double value) {
            requireFinite(value, "base");
            base = value;
        }

        public synchronized void setDefaultBase(double value) {
            requireFinite(value, "defaultBase");
            defaultBase = value;
        }

        public synchronized Modifier modifier(UUID id) {
            return modifiers.get(id);
        }

        public synchronized Collection<Modifier> modifiers() {
            return List.copyOf(modifiers.values());
        }

        public synchronized Collection<UUID> modifierIds() {
            return List.copyOf(modifiers.keySet());
        }

        public synchronized void register(Modifier modifier) {
            modifiers.put(Objects.requireNonNull(modifier, "modifier").id(), modifier);
        }

        public synchronized Modifier remove(UUID id) {
            return modifiers.remove(id);
        }

        public synchronized int removeIf(Predicate<Modifier> predicate) {
            Objects.requireNonNull(predicate, "predicate");
            int before = modifiers.size();
            modifiers.values().removeIf(predicate);
            return before - modifiers.size();
        }

        public synchronized boolean isEmpty() {
            return modifiers.isEmpty();
        }

        public synchronized double total() {
            return total(base, EquipmentSlot.MAIN_HAND, modifier -> true);
        }

        public synchronized double total(EquipmentSlot actionHand) {
            return total(base, actionHand, modifier -> true);
        }

        public synchronized double total(double startingBase, EquipmentSlot actionHand) {
            return total(startingBase, actionHand, modifier -> true);
        }

        public synchronized double filteredTotal(double startingBase, EquipmentSlot actionHand,
                                                 Predicate<Modifier> filter) {
            return total(startingBase, actionHand, Objects.requireNonNull(filter, "filter"));
        }

        private double total(double startingBase, EquipmentSlot actionHand, Predicate<Modifier> filter) {
            requireFinite(startingBase, "startingBase");
            Objects.requireNonNull(actionHand, "actionHand");
            double value = startingBase;
            double additiveMultiplier = 1.0d;
            double relativeMultiplier = 1.0d;
            for (Modifier modifier : modifiers.values()) {
                if (!filter.test(modifier)) continue;
                if (!actionHand.isCompatible(modifier.source(), modifier.slot())) continue;
                switch (modifier.type()) {
                    case FLAT -> value += modifier.value();
                    case ADDITIVE_MULTIPLIER -> additiveMultiplier += modifier.value() / 100.0d;
                    case RELATIVE -> relativeMultiplier *= 1.0d + modifier.value() / 100.0d;
                }
            }
            return value * additiveMultiplier * relativeMultiplier;
        }

        private synchronized int expire(long tick) {
            int before = modifiers.size();
            modifiers.values().removeIf(modifier -> modifier.expired(tick));
            return before - modifiers.size();
        }
    }

    private static final class EntityStats {
        private final Map<String, StatInstance> stats = new ConcurrentHashMap<>();

        private StatInstance instance(String stat) {
            return stats.computeIfAbsent(normalize(stat), StatInstance::new);
        }

        private Collection<StatInstance> instances() {
            return List.copyOf(stats.values());
        }
    }

    private final Map<UUID, EntityStats> entities = new ConcurrentHashMap<>();

    public StatInstance instance(UUID entityId, String stat) {
        Objects.requireNonNull(entityId, "entityId");
        String normalized = normalize(stat);
        if (normalized.isEmpty()) throw new IllegalArgumentException("stat must not be blank");
        return entities.computeIfAbsent(entityId, ignored -> new EntityStats()).instance(normalized);
    }

    public Collection<StatInstance> instances(UUID entityId) {
        EntityStats entity = entities.get(entityId);
        return entity == null ? List.of() : entity.instances();
    }

    public double stat(UUID entityId, String stat) {
        return finalValue(entityId, stat, EquipmentSlot.MAIN_HAND);
    }

    public double finalValue(UUID entityId, String stat, EquipmentSlot actionHand) {
        if (entityId == null || stat == null || stat.isBlank()) return 0.0d;
        EntityStats entity = entities.get(entityId);
        if (entity == null) return 0.0d;
        StatInstance instance = entity.stats.get(normalize(stat));
        return instance == null ? 0.0d : instance.total(actionHand);
    }

    public void setBase(UUID entityId, String stat, double value) {
        instance(entityId, stat).setBase(value);
    }

    public void setDefaultBase(UUID entityId, String stat, double value) {
        instance(entityId, stat).setDefaultBase(value);
    }

    public void register(UUID entityId, String stat, Modifier modifier) {
        instance(entityId, stat).register(modifier);
    }

    public UUID register(UUID entityId, String stat, String key, double value, ModifierType type,
                         EquipmentSlot slot, ModifierSource source) {
        UUID id = UUID.randomUUID();
        register(entityId, stat, new Modifier(id, key, value, type, slot, source));
        return id;
    }

    public UUID registerTemporary(UUID entityId, String stat, String key, double value, ModifierType type,
                                  EquipmentSlot slot, ModifierSource source, long durationTicks,
                                  long currentTick) {
        if (durationTicks < 0L) throw new IllegalArgumentException("durationTicks must be >= 0");
        UUID id = UUID.randomUUID();
        long expiresAt = durationTicks >= Long.MAX_VALUE - currentTick
                ? Long.MAX_VALUE
                : currentTick + durationTicks;
        register(entityId, stat, new Modifier(id, key, value, type, slot, source, expiresAt));
        return id;
    }

    public Modifier remove(UUID entityId, String stat, UUID modifierId) {
        EntityStats entity = entities.get(entityId);
        if (entity == null) return null;
        StatInstance instance = entity.stats.get(normalize(stat));
        return instance == null ? null : instance.remove(modifierId);
    }

    public int removeByKey(UUID entityId, String stat, String key) {
        EntityStats entity = entities.get(entityId);
        if (entity == null) return 0;
        StatInstance instance = entity.stats.get(normalize(stat));
        if (instance == null) return 0;
        String expected = key == null ? "" : key;
        return instance.removeIf(modifier -> modifier.key().equals(expected));
    }

    public int tick(long currentTick) {
        int removed = 0;
        List<UUID> emptyEntities = new ArrayList<>();
        for (Map.Entry<UUID, EntityStats> entityEntry : entities.entrySet()) {
            EntityStats entity = entityEntry.getValue();
            for (StatInstance instance : entity.instances()) removed += instance.expire(currentTick);
            entity.stats.entrySet().removeIf(entry -> entry.getValue().isEmpty()
                    && entry.getValue().base() == 0.0d
                    && entry.getValue().defaultBase() == 0.0d);
            if (entity.stats.isEmpty()) emptyEntities.add(entityEntry.getKey());
        }
        for (UUID entityId : emptyEntities) entities.remove(entityId);
        return removed;
    }

    public void clear(UUID entityId) {
        if (entityId != null) entities.remove(entityId);
    }

    public void clear() {
        entities.clear();
    }

    public int trackedEntities() {
        return entities.size();
    }

    private static String normalize(String stat) {
        return stat == null ? "" : stat.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
