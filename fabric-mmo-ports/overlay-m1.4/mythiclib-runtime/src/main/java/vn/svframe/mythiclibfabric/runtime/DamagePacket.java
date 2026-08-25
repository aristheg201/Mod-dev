package vn.svframe.mythiclibfabric.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MythicLib-compatible damage packet.
 *
 * Original 1.7.1 semantics are value * max(0, 1 + additive) * scalar.
 * The no-arg/add/get/parts helpers are retained for the reconstructed runtime
 * smoke gates and simply aggregate into the original single packet value.
 */
public final class DamagePacket implements Cloneable {
    private List<DamageType> types;
    private double value;
    private double additive;
    private double scalar = 1.0d;
    private String element;
    private final LinkedHashMap<DamageType, Double> compatibilityParts = new LinkedHashMap<>();

    public DamagePacket() {
        this(0.0d, (String) null, List.of());
    }

    public DamagePacket(double value, List<DamageType> types) {
        this(value, null, types);
    }

    public DamagePacket(double value, String element, List<DamageType> types) {
        if (!Double.isFinite(value) || value < 0.0d) throw new IllegalArgumentException("Value cannot be negative or non-finite");
        this.value = value;
        this.types = types == null ? List.of() : new ArrayList<>(types);
        this.element = element;
    }

    public DamagePacket(double value, DamageType... types) {
        this(value, null, types == null ? List.of() : Arrays.asList(types));
    }

    public DamagePacket(double value, String element, DamageType... types) {
        this(value, element, types == null ? List.of() : Arrays.asList(types));
    }

    public double getValue() { return value; }
    public List<DamageType> getTypes() { return types; }
    public String getElement() { return element; }

    public void setTypes(List<DamageType> types) {
        this.types = types == null ? List.of() : new ArrayList<>(types);
    }

    public void setTypes(DamageType[] types) {
        this.types = types == null ? List.of() : new ArrayList<>(Arrays.asList(types));
    }

    public void setValue(double value) {
        if (!Double.isFinite(value) || value < 0.0d) throw new IllegalArgumentException("Value cannot be negative or non-finite");
        this.value = value;
        compatibilityParts.clear();
    }

    public void setElement(String element) { this.element = element; }

    public void multiplicativeModifier(double modifier) {
        if (!Double.isFinite(modifier)) throw new IllegalArgumentException("modifier must be finite");
        scalar *= modifier;
    }

    public void additiveModifier(double modifier) {
        if (!Double.isFinite(modifier)) throw new IllegalArgumentException("modifier must be finite");
        additive += modifier;
    }

    public double getFinalValue() {
        return value * Math.max(0.0d, 1.0d + additive) * scalar;
    }

    public boolean hasType(DamageType type) { return types.contains(type); }
    public boolean hasAnyType(List<DamageType> query) {
        if (query == null) return false;
        for (DamageType type : query) if (types.contains(type)) return true;
        return false;
    }

    public DamagePacket add(DamageType type, double amount) {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(amount) || amount < 0.0d) throw new IllegalArgumentException("invalid damage amount");
        compatibilityParts.merge(type, amount, Double::sum);
        if (!types.contains(type)) {
            ArrayList<DamageType> copy = new ArrayList<>(types);
            copy.add(type);
            types = copy;
        }
        value = compatibilityParts.values().stream().mapToDouble(Double::doubleValue).sum();
        return this;
    }

    public double get(DamageType type) {
        if (!compatibilityParts.isEmpty()) return compatibilityParts.getOrDefault(type, 0.0d);
        return hasType(type) ? value : 0.0d;
    }

    public double total() { return getFinalValue(); }

    public Map<DamageType, Double> parts() {
        if (!compatibilityParts.isEmpty()) return Map.copyOf(compatibilityParts);
        if (types.isEmpty()) return Map.of();
        LinkedHashMap<DamageType, Double> result = new LinkedHashMap<>();
        for (DamageType type : types) result.put(type, value);
        return Map.copyOf(result);
    }

    public DamagePacket copy() { return clone(); }

    @Override
    public DamagePacket clone() {
        DamagePacket copy = new DamagePacket(value, element, types);
        copy.additive = additive;
        copy.scalar = scalar;
        copy.compatibilityParts.putAll(compatibilityParts);
        return copy;
    }

    @Override
    public String toString() {
        return "DamagePacket{value=" + value + ", types=" + types + ", element=" + element + ", additive=" + additive + ", scalar=" + scalar + '}';
    }
}
