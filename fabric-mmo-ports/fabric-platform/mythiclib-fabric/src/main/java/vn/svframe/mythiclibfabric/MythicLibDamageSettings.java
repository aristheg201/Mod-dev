package vn.svframe.mythiclibfabric;

import vn.svframe.compat.YamlLite;
import vn.svframe.mythiclibfabric.runtime.DamageType;
import vn.svframe.mythiclibfabric.runtime.DefenseFormula;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

record MythicLibDamageSettings(
        String naturalFormula,
        String elementalFormula,
        List<DamageType> meleeWeapon,
        List<DamageType> meleeUnarmed,
        List<DamageType> meleeDefault,
        List<DamageType> projectile,
        List<DamageType> skills,
        Map<String, List<DamageType>> sourceMappings) {

    static MythicLibDamageSettings defaults() {
        return new MythicLibDamageSettings(
                DefenseFormula.DEFAULT_NATURAL,
                DefenseFormula.DEFAULT_ELEMENTAL,
                List.of(DamageType.WEAPON, DamageType.PHYSICAL),
                List.of(DamageType.UNARMED, DamageType.PHYSICAL),
                List.of(DamageType.PHYSICAL),
                List.of(DamageType.WEAPON, DamageType.PROJECTILE, DamageType.PHYSICAL),
                List.of(DamageType.SKILL, DamageType.MAGIC),
                Map.of());
    }

    static MythicLibDamageSettings load(Path path) throws IOException {
        MythicLibDamageSettings fallback = defaults();
        if (!Files.isRegularFile(path)) return fallback;
        Map<String, Object> root = YamlLite.map(YamlLite.parse(path));
        Map<String, Object> defense = section(root.get("defense-application"));
        Map<String, Object> damageTypes = section(root.get("damage_types"));
        Map<String, Object> defaults = section(damageTypes.get("default"));
        Map<String, Object> bukkit = section(damageTypes.get("bukkit"));
        Map<String, List<DamageType>> mappings = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : bukkit.entrySet()) {
            try {
                mappings.put(entry.getKey().trim().toUpperCase(Locale.ROOT), DamageType.listFromConfig(entry.getValue()));
            } catch (RuntimeException ignored) {
            }
        }
        return new MythicLibDamageSettings(
                text(defense.get("natural"), fallback.naturalFormula),
                text(defense.get("elemental"), fallback.elementalFormula),
                DamageType.listFromConfig(fallback.meleeWeapon, defaults.get("melee_weapon")),
                DamageType.listFromConfig(fallback.meleeUnarmed, defaults.get("melee_unarmed")),
                DamageType.listFromConfig(fallback.meleeDefault, defaults.get("melee_default")),
                DamageType.listFromConfig(fallback.projectile, defaults.get("bow")),
                DamageType.listFromConfig(fallback.skills, defaults.get("skills")),
                Map.copyOf(mappings));
    }

    List<DamageType> source(String key, List<DamageType> fallback) {
        if (key == null) return fallback;
        return sourceMappings.getOrDefault(key.toUpperCase(Locale.ROOT), fallback);
    }

    private static Map<String, Object> section(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) out.put(String.valueOf(entry.getKey()), entry.getValue());
        return out;
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}
