package io.github.aristheg201.svarcade.engine;

import java.util.List;
import java.util.Map;

/** Immutable authored graph node. Operation codecs validate parameters before play. */
public record EffectDefinition(String op, String selector, Map<String, Double> values,
                               Map<String, String> strings, List<EffectDefinition> children,
                               List<EffectDefinition> otherwise) {
    public EffectDefinition {
        if (op == null || op.isBlank()) throw new IllegalArgumentException("effect.op is required");
        selector = selector == null ? "current_target" : selector;
        values = values == null ? Map.of() : Map.copyOf(values);
        strings = strings == null ? Map.of() : Map.copyOf(strings);
        children = children == null ? List.of() : List.copyOf(children);
        otherwise = otherwise == null ? List.of() : List.copyOf(otherwise);
        if (values.values().stream().anyMatch(v -> !Double.isFinite(v))) throw new IllegalArgumentException("Non-finite effect value");
    }
    public double value(String key, double fallback) { return values.getOrDefault(key, fallback); }
    public String text(String key, String fallback) { return strings.getOrDefault(key, fallback); }
    public static EffectDefinition amount(String op, String selector, double amount) {
        return new EffectDefinition(op, selector, Map.of("amount", amount), Map.of(), List.of(), List.of());
    }
}
