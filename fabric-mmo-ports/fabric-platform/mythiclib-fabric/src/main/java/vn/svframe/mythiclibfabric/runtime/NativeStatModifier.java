package vn.svframe.mythiclibfabric.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Native Fabric implementation of MythicLib 1.7.1 StatModifier. */
public class NativeStatModifier extends NativeInstanceModifier {
    private final String stat;

    public NativeStatModifier(String key, String stat, double value) {
        this(key, stat, value, NativeStatEngine.ModifierType.FLAT,
                NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER);
    }

    public NativeStatModifier(String key, String stat, double value, NativeStatEngine.ModifierType type) {
        this(key, stat, value, type,
                NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER);
    }

    public NativeStatModifier(String key,
                              String stat,
                              double value,
                              NativeStatEngine.ModifierType type,
                              NativeStatEngine.EquipmentSlot slot,
                              NativeStatEngine.ModifierSource source) {
        this(UUID.randomUUID(), key, stat, value, type, slot, source);
    }

    public NativeStatModifier(UUID uniqueId,
                              String key,
                              String stat,
                              double value,
                              NativeStatEngine.ModifierType type,
                              NativeStatEngine.EquipmentSlot slot,
                              NativeStatEngine.ModifierSource source) {
        super(uniqueId, key, slot, source, value, type);
        this.stat = Objects.requireNonNull(stat, "stat");
    }

    public NativeStatModifier(String key, String stat, String encodedValue) {
        super(key, NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER, encodedValue);
        this.stat = Objects.requireNonNull(stat, "stat");
    }

    public NativeStatModifier(Map<String, ?> config) {
        super(config);
        this.stat = Objects.requireNonNull(string(config, "stat", null), "stat");
    }

    public String stat() {
        return stat;
    }

    public NativeStatModifier add(double amount) {
        return new NativeStatModifier(uniqueId(), key(), stat, value() + amount, type(), slot(), source());
    }

    public NativeStatModifier multiply(double multiplier) {
        return new NativeStatModifier(uniqueId(), key(), stat, value() * multiplier, type(), slot(), source());
    }

    public void register(NativeStatEngine engine, UUID entityId) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(entityId, "entityId");
        engine.register(entityId, stat, asEngineModifier());
    }

    public void unregister(NativeStatEngine engine, UUID entityId) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(entityId, "entityId");
        engine.remove(entityId, stat, uniqueId());
    }
}
