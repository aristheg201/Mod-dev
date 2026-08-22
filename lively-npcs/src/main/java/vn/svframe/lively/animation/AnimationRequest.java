package vn.svframe.lively.animation;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable animation request shared by every Lively body implementation.
 *
 * <p>The core module deliberately knows nothing about Cobblemon. Integrations may interpret additional names and
 * parameters while vanilla bodies simply reject unsupported requests.</p>
 */
public record AnimationRequest(String name, Map<String, String> parameters) {
    public AnimationRequest {
        name = normalize(name);
        if (name.isBlank()) throw new IllegalArgumentException("animation name must not be blank");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public static AnimationRequest named(String name) {
        return new AnimationRequest(name, Map.of());
    }

    public boolean is(String value) {
        return name.equals(normalize(value));
    }

    public String parameter(String key, String fallback) {
        String value = parameters.get(Objects.requireNonNull(key));
        return value == null || value.isBlank() ? fallback : value;
    }

    public boolean booleanParameter(String key, boolean fallback) {
        String value = parameters.get(Objects.requireNonNull(key));
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
