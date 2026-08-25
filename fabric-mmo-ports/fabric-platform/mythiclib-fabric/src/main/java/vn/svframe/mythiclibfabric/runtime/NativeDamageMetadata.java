package vn.svframe.mythiclibfabric.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native Fabric representation of MythicLib 1.7.1 DamageMetadata. */
public final class NativeDamageMetadata implements Cloneable {
    public static final double MINIMAL_DAMAGE = 0.01d;

    private final List<DamagePacket> packets = new ArrayList<>();
    private final DamagePacket initialPacket;
    private final List<String> critTags = new ArrayList<>();

    public NativeDamageMetadata() {
        this(0.0d, List.of());
    }

    public NativeDamageMetadata(double value, DamageType... types) {
        this(value, types == null ? List.of() : Arrays.asList(types));
    }

    public NativeDamageMetadata(double value, List<DamageType> types) {
        this(new DamagePacket(value, types));
    }

    public NativeDamageMetadata(double value, String element, DamageType... types) {
        this(new DamagePacket(value, element, types));
    }

    public NativeDamageMetadata(double value, String element, List<DamageType> types) {
        this(new DamagePacket(value, element, types));
    }

    public NativeDamageMetadata(DamagePacket initialPacket) {
        if (initialPacket == null) throw new NullPointerException("Initial packet cannot be null");
        this.initialPacket = initialPacket;
        this.packets.add(initialPacket);
    }

    public DamagePacket initialPacket() { return initialPacket; }
    public List<DamagePacket> packets() { return List.copyOf(packets); }

    public double damage() {
        double total = 0.0d;
        for (DamagePacket packet : packets) total += packet.getFinalValue();
        return Math.max(MINIMAL_DAMAGE, total);
    }

    public double damage(DamageType type) {
        double total = 0.0d;
        for (DamagePacket packet : packets) if (packet.hasType(type)) total += packet.getFinalValue();
        return total;
    }

    public double damage(String element) {
        double total = 0.0d;
        for (DamagePacket packet : packets) {
            if (java.util.Objects.equals(normalizeElement(packet.getElement()), normalizeElement(element))) total += packet.getFinalValue();
        }
        return total;
    }

    public Set<DamageType> collectTypes() {
        LinkedHashSet<DamageType> result = new LinkedHashSet<>();
        for (DamagePacket packet : packets) result.addAll(packet.getTypes());
        return result;
    }

    public boolean hasAnyType(List<DamageType> types) {
        for (DamagePacket packet : packets) if (packet.hasAnyType(types)) return true;
        return false;
    }

    public boolean hasType(DamageType type) {
        for (DamagePacket packet : packets) if (packet.hasType(type)) return true;
        return false;
    }

    public boolean hasElement(String element) {
        String normalized = normalizeElement(element);
        for (DamagePacket packet : packets) if (java.util.Objects.equals(normalized, normalizeElement(packet.getElement()))) return true;
        return false;
    }

    public NativeDamageMetadata add(double value, DamageType... types) {
        return add(value, types == null ? List.of() : Arrays.asList(types));
    }

    public NativeDamageMetadata add(double value, List<DamageType> types) {
        packets.add(new DamagePacket(value, types));
        return this;
    }

    public NativeDamageMetadata add(double value, String element, DamageType... types) {
        packets.add(new DamagePacket(value, element, types));
        return this;
    }

    public NativeDamageMetadata add(double value, String element, List<DamageType> types) {
        packets.add(new DamagePacket(value, element, types));
        return this;
    }

    public NativeDamageMetadata multiplicativeModifier(double modifier) {
        for (DamagePacket packet : packets) packet.multiplicativeModifier(modifier);
        return this;
    }

    public NativeDamageMetadata additiveModifier(double modifier) {
        for (DamagePacket packet : packets) packet.additiveModifier(modifier);
        return this;
    }

    public NativeDamageMetadata multiplicativeModifier(double modifier, DamageType type) {
        for (DamagePacket packet : packets) if (packet.hasType(type)) packet.multiplicativeModifier(modifier);
        return this;
    }

    public NativeDamageMetadata multiplicativeModifier(double modifier, List<DamageType> types) {
        for (DamagePacket packet : packets) if (packet.hasAnyType(types)) packet.multiplicativeModifier(modifier);
        return this;
    }

    public NativeDamageMetadata multiplicativeModifier(double modifier, String element) {
        String normalized = normalizeElement(element);
        for (DamagePacket packet : packets) if (java.util.Objects.equals(normalized, normalizeElement(packet.getElement()))) packet.multiplicativeModifier(modifier);
        return this;
    }

    public NativeDamageMetadata additiveModifier(double modifier, DamageType type) {
        for (DamagePacket packet : packets) if (packet.hasType(type)) packet.additiveModifier(modifier);
        return this;
    }

    public NativeDamageMetadata additiveModifier(double modifier, String element) {
        String normalized = normalizeElement(element);
        for (DamagePacket packet : packets) if (java.util.Objects.equals(normalized, normalizeElement(packet.getElement()))) packet.additiveModifier(modifier);
        return this;
    }

    public Set<String> collectElements() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (DamagePacket packet : packets) if (packet.getElement() != null) result.add(normalizeElement(packet.getElement()));
        return result;
    }

    public Map<String, Double> elementalDamage() {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (DamagePacket packet : packets) {
            if (packet.getElement() != null) result.merge(normalizeElement(packet.getElement()), packet.getFinalValue(), Double::sum);
        }
        return Map.copyOf(result);
    }

    public boolean isWeaponCriticalStrike() { return critTags.contains("weapon"); }
    public void registerWeaponCriticalStrike() { critTags.add("weapon"); }
    public boolean isSkillCriticalStrike() { return critTags.contains("skill"); }
    public void registerSkillCriticalStrike() { critTags.add("skill"); }
    public void registerCrits(List<String> tags) { if (tags != null) critTags.addAll(tags); }
    public boolean isElementalCriticalStrike(String element) { return critTags.contains(normalizeElement(element)); }
    public void registerElementalCriticalStrike(String element) { if (element != null) critTags.add(normalizeElement(element)); }
    public List<String> critTags() { return List.copyOf(critTags); }

    @Override
    public NativeDamageMetadata clone() {
        NativeDamageMetadata copy = new NativeDamageMetadata(initialPacket.clone());
        copy.packets.clear();
        for (DamagePacket packet : packets) copy.packets.add(packet.clone());
        copy.critTags.addAll(critTags);
        return copy;
    }

    private static String normalizeElement(String element) {
        return element == null ? null : element.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
