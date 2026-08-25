package vn.svframe.mythiclibfabric.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Damage type surface aligned with MythicLib 1.7.1. */
public enum DamageType {
    MAGIC,
    PHYSICAL,
    WEAPON,
    SKILL,
    PROJECTILE,
    UNARMED,
    ON_HIT,
    MINION,
    DOT;

    public String getPath() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getOffenseStat() {
        return name() + "_DAMAGE";
    }

    public static List<DamageType> listFromConfig(List<DamageType> fallback, Object raw) {
        if (raw == null) return Objects.requireNonNull(fallback, "Fallback damage types cannot be null");
        return listFromConfig(raw);
    }

    public static List<DamageType> listFromConfig(Object raw) {
        if (raw instanceof String text) {
            String[] split = text.split(",");
            List<DamageType> result = new ArrayList<>(split.length);
            for (String value : split) result.add(parse(value));
            return result;
        }
        if (raw instanceof List<?> list) {
            List<DamageType> result = new ArrayList<>(list.size());
            for (Object value : list) result.add(parse(String.valueOf(value)));
            return result;
        }
        throw new IllegalArgumentException("Cannot parse DamageType list from " + raw);
    }

    private static DamageType parse(String value) {
        String normalized = Objects.requireNonNull(value, "damage type").trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("No damage type with name '" + value + "'", ex);
        }
    }
}
