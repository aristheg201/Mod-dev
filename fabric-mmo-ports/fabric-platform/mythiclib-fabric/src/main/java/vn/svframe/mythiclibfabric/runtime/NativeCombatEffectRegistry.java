package vn.svframe.mythiclibfabric.runtime;

import vn.svframe.mythiclibfabric.runtime.script.ExpressionRuntime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native Fabric port of MythicLib 1.7.1 MitigationType/OnHitEffect registries. */
public final class NativeCombatEffectRegistry {
    public enum Kind { MITIGATION, ON_HIT }

    private final Kind kind;
    private final Map<String, Effect> effects = new LinkedHashMap<>();

    public NativeCombatEffectRegistry(Kind kind) {
        this.kind = java.util.Objects.requireNonNull(kind, "kind");
    }

    public synchronized void clear() { effects.clear(); }

    public synchronized void load(Map<String, Object> root) {
        LinkedHashMap<String, Effect> next = new LinkedHashMap<>();
        if (root != null) {
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> section = (Map<String, Object>) raw;
                Effect effect = Effect.from(kind, entry.getKey(), section);
                next.put(effect.id(), effect);
            }
        }
        effects.clear();
        effects.putAll(next);
    }

    public synchronized int size() { return effects.size(); }
    public synchronized List<Effect> values() { return List.copyOf(effects.values()); }
    public synchronized Effect get(String id) { return effects.get(normalizeId(id)); }

    public static final class Effect {
        private static final ExpressionRuntime EXPRESSIONS = new ExpressionRuntime();

        private final Kind kind;
        private final String id;
        private final String cooldownPath;
        private final boolean skipEvent;
        private final String legacy;
        private final String cooldownFormula;
        private final String rollFormula;
        private final String onScript;
        private final String preScript;

        private Effect(Kind kind, String id, boolean skipEvent, String legacy, String cooldownFormula,
                       String rollFormula, String onScript, String preScript) {
            this.kind = kind;
            this.id = id;
            // MythicLib 1.7.1 uses the same historical "mitigation:" cooldown namespace
            // for both MitigationType and OnHitEffect. Preserve that observable behavior.
            this.cooldownPath = "mitigation:" + id;
            this.skipEvent = skipEvent;
            this.legacy = legacy;
            this.cooldownFormula = cooldownFormula;
            this.rollFormula = rollFormula;
            this.onScript = java.util.Objects.requireNonNull(onScript,
                    kind == Kind.MITIGATION ? "Could not find on_damage skill" : "Could not find on_attack skill");
            this.preScript = preScript;
        }

        public Kind kind() { return kind; }
        public String id() { return id; }
        public String cooldownPath() { return cooldownPath; }
        public boolean skipEvent() { return skipEvent; }
        public String legacy() { return legacy; }
        public String cooldownFormula() { return cooldownFormula; }
        public String rollFormula() { return rollFormula; }
        public String onScript() { return onScript; }
        public String preScript() { return preScript; }
        public boolean hasCooldown() { return cooldownFormula != null; }
        public boolean hasRoll() { return rollFormula != null; }

        public double cooldown(Map<String, Double> variables) {
            return hasCooldown() ? EXPRESSIONS.evaluate(cooldownFormula, variables == null ? Map.of() : variables) : 0.0d;
        }

        public double roll(Map<String, Double> variables) {
            return hasRoll() ? EXPRESSIONS.evaluate(rollFormula, variables == null ? Map.of() : variables) : 1.0d;
        }

        private static Effect from(Kind kind, String rawId, Map<String, Object> section) {
            String id = rawId == null ? "" : rawId;
            String onKey = kind == Kind.MITIGATION ? "on_damage" : "on_attack";
            String preKey = kind == Kind.MITIGATION ? "pre_damage" : "pre_attack";
            return new Effect(
                    kind,
                    id,
                    bool(section.get("skip_event"), false),
                    nullable(section.get("legacy")),
                    nullable(section.get("cooldown")),
                    nullable(section.get("roll")),
                    nullable(section.get(onKey)),
                    nullable(section.get(preKey)));
        }
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String nullable(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
