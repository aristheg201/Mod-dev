package vn.svframe.mythiclibfabric.runtime;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Native Fabric implementation of MythicLib 1.7.1 InstanceModifier/PlayerModifier value semantics. */
public abstract class NativeInstanceModifier {
    private static final DecimalFormat DEFAULT_DECIMAL = new DecimalFormat("0.#");

    private final UUID uniqueId;
    private final String key;
    private final NativeStatEngine.EquipmentSlot slot;
    private final NativeStatEngine.ModifierSource source;
    private final double value;
    private final NativeStatEngine.ModifierType type;

    protected NativeInstanceModifier(String key, double value) {
        this(UUID.randomUUID(), key, NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER, value, NativeStatEngine.ModifierType.FLAT);
    }

    protected NativeInstanceModifier(String key,
                                     NativeStatEngine.EquipmentSlot slot,
                                     NativeStatEngine.ModifierSource source,
                                     double value,
                                     NativeStatEngine.ModifierType type) {
        this(UUID.randomUUID(), key, slot, source, value, type);
    }

    protected NativeInstanceModifier(UUID uniqueId,
                                     String key,
                                     NativeStatEngine.EquipmentSlot slot,
                                     NativeStatEngine.ModifierSource source,
                                     double value,
                                     NativeStatEngine.ModifierType type) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.key = key;
        this.slot = Objects.requireNonNull(slot, "slot");
        this.source = Objects.requireNonNull(source, "source");
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Modifier value must be finite");
        this.value = value;
        this.type = Objects.requireNonNull(type, "type");
    }

    protected NativeInstanceModifier(String key,
                                     NativeStatEngine.EquipmentSlot slot,
                                     NativeStatEngine.ModifierSource source,
                                     String encodedValue) {
        this(UUID.randomUUID(), key, slot, source, parse(encodedValue));
    }

    protected NativeInstanceModifier(Map<String, ?> config) {
        this(UUID.randomUUID(),
                string(config, "key", null),
                NativeStatEngine.EquipmentSlot.OTHER,
                NativeStatEngine.ModifierSource.OTHER,
                number(config, "value", 0.0d),
                bool(config, "multiplicative", false)
                        ? NativeStatEngine.ModifierType.RELATIVE
                        : bool(config, "scalar", false)
                        ? NativeStatEngine.ModifierType.ADDITIVE_MULTIPLIER
                        : NativeStatEngine.ModifierType.FLAT);
    }

    private NativeInstanceModifier(UUID uniqueId,
                                   String key,
                                   NativeStatEngine.EquipmentSlot slot,
                                   NativeStatEngine.ModifierSource source,
                                   Parsed parsed) {
        this(uniqueId, key, slot, source, parsed.value(), parsed.type());
    }

    public final UUID uniqueId() {
        return uniqueId;
    }

    public final String key() {
        return key;
    }

    public final NativeStatEngine.EquipmentSlot slot() {
        return slot;
    }

    public final NativeStatEngine.ModifierSource source() {
        return source;
    }

    public final double value() {
        return value;
    }

    public final NativeStatEngine.ModifierType type() {
        return type;
    }

    public final NativeStatEngine.Modifier asEngineModifier() {
        return new NativeStatEngine.Modifier(uniqueId, key, value, type, slot, source);
    }

    @Override
    public String toString() {
        String formatted;
        synchronized (DEFAULT_DECIMAL) {
            formatted = DEFAULT_DECIMAL.format(value);
        }
        return formatted + switch (type) {
            case RELATIVE -> "%";
            case ADDITIVE_MULTIPLIER -> "s";
            case FLAT -> "";
        };
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NativeInstanceModifier that = (NativeInstanceModifier) obj;
        return uniqueId.equals(that.uniqueId);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uniqueId);
    }

    protected static Parsed parse(String encodedValue) {
        if (encodedValue == null) throw new IllegalArgumentException("String cannot be null");
        if (encodedValue.isEmpty()) throw new IllegalArgumentException("String cannot be empty");
        char suffix = encodedValue.charAt(encodedValue.length() - 1);
        NativeStatEngine.ModifierType type = switch (suffix) {
            case '%', 'c', 'm' -> NativeStatEngine.ModifierType.RELATIVE;
            case 'a', 's' -> NativeStatEngine.ModifierType.ADDITIVE_MULTIPLIER;
            default -> NativeStatEngine.ModifierType.FLAT;
        };
        String number = type == NativeStatEngine.ModifierType.FLAT
                ? encodedValue
                : encodedValue.substring(0, encodedValue.length() - 1);
        return new Parsed(type, Double.parseDouble(number));
    }

    protected static String string(Map<String, ?> config, String key, String fallback) {
        Objects.requireNonNull(config, "config");
        Object value = config.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    protected static double number(Map<String, ?> config, String key, double fallback) {
        Objects.requireNonNull(config, "config");
        Object value = config.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(String.valueOf(value).trim());
    }

    protected static boolean bool(Map<String, ?> config, String key, boolean fallback) {
        Objects.requireNonNull(config, "config");
        Object value = config.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        return switch (String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> fallback;
        };
    }

    protected record Parsed(NativeStatEngine.ModifierType type, double value) {}
}
