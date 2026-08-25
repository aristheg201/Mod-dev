package vn.svframe.mythiclibfabric.runtime.script;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Mutable execution context for native MythicLib scripts. */
public final class ScriptContext {
    /** Live bridge used by combat scripts to mutate the real damage metadata. */
    public interface DamageBridge {
        double total();
        double type(String type);
        double element(String element);
        void setTotal(double amount);
        void setType(String type, double amount);
        void setElement(String element, double amount);
        void multiplyAll(double coefficient);
        void multiplyType(String type, double coefficient);
        void multiplyElement(String element, double coefficient);
        void additiveAll(double multiplier);
        void additiveType(String type, double multiplier);
    }

    private final UUID caster;
    private UUID target;
    private double damage;
    private boolean cancelled;
    private Vector3 targetLocation;
    private DamageBridge damageBridge;
    private final Map<String, Double> numbers = new HashMap<>();
    private final Map<String, Vector3> vectors = new HashMap<>();
    private final Map<String, Object> objects = new HashMap<>();
    private final Set<String> damageTypes = new HashSet<>();
    private final Map<String, Double> damageByType = new HashMap<>();
    private final Map<String, Double> damageByElement = new HashMap<>();

    public ScriptContext(UUID caster, UUID target) {
        this.caster = Objects.requireNonNull(caster, "caster");
        this.target = target;
    }

    public UUID caster() { return caster; }
    public UUID target() { return target; }
    public void target(UUID value) { target = value; }

    public double damage() { return damageBridge == null ? damage : damageBridge.total(); }
    public void damage(double value) {
        damage = value;
        numbers.put("attack.damage", value);
        if (damageBridge != null) damageBridge.setTotal(value);
    }

    public double damage(String type) {
        String normalized = normalize(type);
        return damageBridge == null ? damageByType.getOrDefault(normalized, 0.0d) : damageBridge.type(normalized);
    }

    public void damage(String type, double value) {
        String normalized = normalize(type);
        damageByType.put(normalized, value);
        numbers.put("attack.damage_" + normalized.toLowerCase(java.util.Locale.ROOT), value);
        if (damageBridge != null) damageBridge.setType(normalized, value);
    }

    public double elementDamage(String element) {
        String normalized = normalize(element);
        return damageBridge == null ? damageByElement.getOrDefault(normalized, 0.0d) : damageBridge.element(normalized);
    }

    public void elementDamage(String element, double value) {
        String normalized = normalize(element);
        damageByElement.put(normalized, value);
        numbers.put("attack.element_" + normalized.toLowerCase(java.util.Locale.ROOT), value);
        if (damageBridge != null) damageBridge.setElement(normalized, value);
    }

    public boolean cancelled() { return cancelled; }
    public void cancel() { cancelled = true; }
    public void uncancel() { cancelled = false; }

    public Map<String, Double> numbers() { return numbers; }
    public Map<String, Vector3> vectors() { return vectors; }
    public Map<String, Object> objects() { return objects; }
    public Set<String> damageTypes() { return damageTypes; }
    public Map<String, Double> damageByType() { return damageByType; }
    public Map<String, Double> damageByElement() { return damageByElement; }

    public Vector3 targetLocation() { return targetLocation; }
    public void targetLocation(Vector3 value) { targetLocation = value; }

    public DamageBridge damageBridge() { return damageBridge; }
    public void bindDamageBridge(DamageBridge bridge) { damageBridge = bridge; }

    public ScriptContext copy() {
        ScriptContext copy = new ScriptContext(caster, target);
        copy.damage = damage;
        copy.cancelled = cancelled;
        copy.targetLocation = targetLocation;
        copy.damageBridge = damageBridge;
        copy.numbers.putAll(numbers);
        copy.vectors.putAll(vectors);
        copy.objects.putAll(objects);
        copy.damageTypes.addAll(damageTypes);
        copy.damageByType.putAll(damageByType);
        copy.damageByElement.putAll(damageByElement);
        return copy;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
