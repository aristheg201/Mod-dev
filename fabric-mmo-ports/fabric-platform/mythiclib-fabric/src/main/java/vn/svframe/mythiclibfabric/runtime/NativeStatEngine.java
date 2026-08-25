package vn.svframe.mythiclibfabric.runtime;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Native Fabric implementation of MythicLib 1.7.1 stat-instance and stat-map semantics.
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
        ARMOR(true, false),
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
        private static final DecimalFormat DEFAULT_DECIMAL = new DecimalFormat("0.##");

        private final NativeStatEngine owner;
        private final UUID entityId;
        private final String stat;
        private final Map<UUID, Modifier> modifiers = new LinkedHashMap<>();
        private double explicitBase;
        private double explicitDefaultBase;

        private StatInstance(NativeStatEngine owner, UUID entityId, String stat) {
            this.owner = owner;
            this.entityId = entityId;
            this.stat = stat;
        }

        public UUID entityId() {
            return entityId;
        }

        public String stat() {
            return stat;
        }

        public synchronized double base() {
            NativeStatHandler handler = owner.handler(stat);
            return handler == null ? explicitBase : handler.getBaseValue(this);
        }

        public synchronized double defaultBase() {
            NativeStatHandler handler = owner.handler(stat);
            return handler == null ? explicitDefaultBase : handler.getPlayerDefaultBase();
        }

        public synchronized void setBase(double value) {
            requireFinite(value, "base");
            explicitBase = value;
            notifyUpdate();
        }

        public synchronized void setDefaultBase(double value) {
            requireFinite(value, "defaultBase");
            explicitDefaultBase = value;
            notifyUpdate();
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
            notifyUpdate();
        }

        public synchronized Modifier remove(UUID id) {
            Modifier removed = modifiers.remove(id);
            if (removed != null) notifyUpdate();
            return removed;
        }

        public synchronized int removeIf(Predicate<Modifier> predicate) {
            Objects.requireNonNull(predicate, "predicate");
            int before = modifiers.size();
            modifiers.values().removeIf(predicate);
            int removed = before - modifiers.size();
            if (removed > 0) notifyUpdate();
            return removed;
        }

        public synchronized boolean isEmpty() {
            return modifiers.isEmpty();
        }

        public synchronized double total() {
            return total(base(), EquipmentSlot.MAIN_HAND, modifier -> true);
        }

        public synchronized double total(EquipmentSlot actionHand) {
            return total(base(), actionHand, modifier -> true);
        }

        public synchronized double total(double startingBase, EquipmentSlot actionHand) {
            return total(startingBase, actionHand, modifier -> true);
        }

        public synchronized double filteredTotal(double startingBase, EquipmentSlot actionHand,
                                                 Predicate<Modifier> filter) {
            return total(startingBase, actionHand, Objects.requireNonNull(filter, "filter"));
        }

        public synchronized String formatFinal() {
            return format(finalValue(EquipmentSlot.MAIN_HAND));
        }

        public synchronized String format(double value) {
            NativeStatHandler handler = owner.handler(stat);
            if (handler != null) return handler.format(value);
            synchronized (DEFAULT_DECIMAL) {
                return DEFAULT_DECIMAL.format(value);
            }
        }

        public synchronized double finalValue(EquipmentSlot actionHand) {
            NativeStatHandler handler = owner.handler(stat);
            return handler == null ? total(actionHand) : handler.getFinalValue(this, actionHand);
        }

        private double total(double startingBase, EquipmentSlot actionHand, Predicate<Modifier> filter) {
            requireFinite(startingBase, "startingBase");
            Objects.requireNonNull(actionHand, "actionHand");
            NativeStatHandler handler = owner.handler(stat);
            NativeStatHandler.ModifierEditor editor = handler == null ? null : handler.modifierEditor();
            double value = startingBase;
            double additiveMultiplier = 1.0d;
            double relativeMultiplier = 1.0d;
            for (Modifier original : modifiers.values()) {
                if (!filter.test(original)) continue;
                if (!actionHand.isCompatible(original.source(), original.slot())) continue;
                Modifier modifier = editor == null ? original : editor.apply(this, original);
                if (modifier == null) continue;
                switch (modifier.type()) {
                    case FLAT -> value += modifier.value();
                    case ADDITIVE_MULTIPLIER -> additiveMultiplier += modifier.value() / 100.0d;
                    case RELATIVE -> relativeMultiplier *= 1.0d + modifier.value() / 100.0d;
                }
            }
            value = value * additiveMultiplier * relativeMultiplier;
            return handler == null ? value : handler.clampValue(value);
        }

        private synchronized int expire(long tick) {
            int before = modifiers.size();
            modifiers.values().removeIf(modifier -> modifier.expired(tick));
            int removed = before - modifiers.size();
            if (removed > 0) notifyUpdate();
            return removed;
        }

        private void notifyUpdate() {
            owner.requestUpdate(entityId, stat, this);
        }
    }

    private static final class EntityStats {
        private final NativeStatEngine owner;
        private final UUID entityId;
        private final Map<String, StatInstance> stats = new ConcurrentHashMap<>();
        private final AtomicInteger updatesBuffered = new AtomicInteger();
        private final Set<String> dirtyStats = ConcurrentHashMap.newKeySet();
        private volatile boolean sessionOpen;

        private EntityStats(NativeStatEngine owner, UUID entityId) {
            this.owner = owner;
            this.entityId = entityId;
        }

        private StatInstance instance(String stat) {
            return stats.computeIfAbsent(normalize(stat), value -> new StatInstance(owner, entityId, value));
        }

        private Collection<StatInstance> instances() {
            return List.copyOf(stats.values());
        }

        private boolean bufferingUpdates() {
            return updatesBuffered.get() > 0 || !sessionOpen;
        }

        private void markDirty(String stat) {
            dirtyStats.add(normalize(stat));
        }

        private void releaseDirty() {
            if (bufferingUpdates()) return;
            List<String> pending = List.copyOf(dirtyStats);
            dirtyStats.removeAll(pending);
            for (String stat : pending) {
                StatInstance instance = stats.get(stat);
                if (instance != null) owner.runUpdate(instance);
            }
        }
    }

    private final Map<UUID, EntityStats> entities = new ConcurrentHashMap<>();
    private final Map<String, NativeStatHandler> handlers = new ConcurrentHashMap<>();

    public NativeStatHandler registerHandler(NativeStatHandler handler) {
        Objects.requireNonNull(handler, "handler");
        return handlers.put(normalize(handler.stat()), handler);
    }

    public NativeStatHandler removeHandler(String stat) {
        return handlers.remove(normalize(stat));
    }

    public NativeStatHandler handler(String stat) {
        return handlers.get(normalize(stat));
    }

    public Collection<NativeStatHandler> handlers() {
        return List.copyOf(handlers.values());
    }

    public StatInstance instance(UUID entityId, String stat) {
        Objects.requireNonNull(entityId, "entityId");
        String normalized = normalize(stat);
        if (normalized.isEmpty()) throw new IllegalArgumentException("stat must not be blank");
        return entity(entityId).instance(normalized);
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
        return instance == null ? 0.0d : instance.finalValue(actionHand);
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

    public boolean isBufferingUpdates(UUID entityId) {
        EntityStats entity = entities.get(entityId);
        return entity != null && entity.bufferingUpdates();
    }

    public void bufferUpdates(UUID entityId, Runnable operation) {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(operation, "operation");
        EntityStats entity = entity(entityId);
        entity.updatesBuffered.incrementAndGet();
        try {
            operation.run();
        } finally {
            if (entity.updatesBuffered.decrementAndGet() == 0 && entity.sessionOpen) entity.releaseDirty();
        }
    }

    public void onSessionOpen(UUID entityId) {
        Objects.requireNonNull(entityId, "entityId");
        EntityStats entity = entity(entityId);
        entity.sessionOpen = true;
        for (NativeStatHandler handler : handlers.values()) {
            StatInstance instance = handler.updateOnLogin()
                    ? entity.instance(handler.stat())
                    : entity.stats.get(normalize(handler.stat()));
            if (instance != null) {
                entity.markDirty(instance.stat());
            }
        }
        entity.releaseDirty();
    }

    public void onSessionClose(UUID entityId) {
        EntityStats entity = entities.get(entityId);
        if (entity == null) return;
        entity.sessionOpen = false;
        entity.dirtyStats.clear();
    }

    public void update(UUID entityId, String stat) {
        EntityStats entity = entities.get(entityId);
        if (entity == null) return;
        StatInstance instance = entity.stats.get(normalize(stat));
        if (instance == null) return;
        requestUpdate(entityId, stat, instance);
    }

    public int tick(long currentTick) {
        int removed = 0;
        List<UUID> emptyEntities = new ArrayList<>();
        for (Map.Entry<UUID, EntityStats> entityEntry : entities.entrySet()) {
            EntityStats entity = entityEntry.getValue();
            for (StatInstance instance : entity.instances()) removed += instance.expire(currentTick);
            entity.stats.entrySet().removeIf(entry -> entry.getValue().isEmpty()
                    && entry.getValue().base() == 0.0d
                    && entry.getValue().defaultBase() == 0.0d
                    && handler(entry.getKey()) == null);
            if (entity.stats.isEmpty() && !entity.sessionOpen) emptyEntities.add(entityEntry.getKey());
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

    private EntityStats entity(UUID entityId) {
        return entities.computeIfAbsent(entityId, id -> new EntityStats(this, id));
    }

    private void requestUpdate(UUID entityId, String stat, StatInstance instance) {
        EntityStats entity = entities.get(entityId);
        if (entity == null) return;
        if (entity.bufferingUpdates()) {
            entity.markDirty(stat);
            return;
        }
        runUpdate(instance);
    }

    private void runUpdate(StatInstance instance) {
        NativeStatHandler handler = handler(instance.stat());
        if (handler != null) handler.runUpdates(instance);
    }

    private static String normalize(String stat) {
        return stat == null ? "" : stat.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
