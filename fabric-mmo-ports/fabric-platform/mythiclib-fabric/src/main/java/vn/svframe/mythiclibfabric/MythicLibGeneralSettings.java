package vn.svframe.mythiclibfabric;

import vn.svframe.compat.YamlLite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Parsed native counterpart of MythicLib 1.7.1 general config.yml options. */
public record MythicLibGeneralSettings(
        int configVersion,
        boolean debug,
        int maxSyncTries,
        char decimalSeparator,
        HealthScale healthScale,
        boolean ignoreShiftTriggers,
        boolean ignoreOffhandClickTriggers,
        String hologramProvider,
        String levelPlugin,
        String classPlugin,
        String manaPlugin,
        boolean skillFlagChecks,
        DamageParticles damageParticles,
        boolean fixMovementSpeed,
        boolean fixTooLargePackets,
        AttributeModifierReset attributeModifierReset,
        BuiltinMana builtinMana) {

    public static MythicLibGeneralSettings load(Path file) throws IOException {
        Map<String, Object> root = Files.isRegularFile(file) ? YamlLite.map(YamlLite.parse(file)) : Map.of();
        Map<String, Object> number = map(root.get("number-format"));
        Map<String, Object> health = map(root.get("health-scale"));
        Map<String, Object> flags = map(root.get("enable_flag_checks"));
        Map<String, Object> particles = map(root.get("damage-particles-cap"));
        Map<String, Object> reset = map(root.get("fix_reset_attribute_modifiers"));
        Map<String, Object> mana = map(root.get("builtin_mana"));
        Map<String, Object> loginRatio = map(mana.get("login_ratio"));

        String decimal = string(number.get("decimal-separator"), ".");
        List<String> attributes = strings(reset.get("attributes"));
        return new MythicLibGeneralSettings(
                integer(root.get("config-version"), 1),
                bool(root.get("debug"), false),
                integer(root.get("max-sync-tries"), 5),
                decimal.isEmpty() ? '.' : decimal.charAt(0),
                new HealthScale(bool(health.get("enabled"), false), number(health.get("scale"), 20.0d), integer(health.get("delay"), 0)),
                bool(root.get("ignore_shift_triggers"), false),
                bool(root.get("ignore_offhand_click_triggers"), false),
                string(root.get("hologram-provider"), "TEXT_DISPLAYS"),
                string(root.get("level-plugin"), "MMOCORE"),
                string(root.get("class-plugin"), "MMOCORE"),
                string(root.get("mana-plugin"), "MMOCORE"),
                bool(flags.get("skills"), true),
                new DamageParticles(bool(particles.get("enabled"), false), integer(particles.get("max-per-tick"), 10)),
                bool(root.get("fix-movement-speed"), false),
                bool(root.get("fix-too-large-packets"), false),
                new AttributeModifierReset(bool(reset.get("enabled"), false), integer(reset.get("rev_id"), 1), attributes),
                new BuiltinMana(integer(mana.get("refresh_rate"), 10), number(loginRatio.get("mana"), 100.0d), number(loginRatio.get("stamina"), 100.0d)));
    }

    public record HealthScale(boolean enabled, double scale, int delayTicks) {
        public HealthScale {
            if (!Double.isFinite(scale) || scale <= 0.0d) scale = 20.0d;
            delayTicks = Math.max(0, delayTicks);
        }
    }

    public record DamageParticles(boolean enabled, int maxPerTick) {
        public DamageParticles { maxPerTick = Math.max(0, maxPerTick); }
    }

    public record AttributeModifierReset(boolean enabled, int revisionId, List<String> attributes) {
        public AttributeModifierReset { attributes = attributes == null ? List.of() : List.copyOf(attributes); }
    }

    public record BuiltinMana(int refreshRateTicks, double loginManaPercent, double loginStaminaPercent) {
        public BuiltinMana { refreshRateTicks = Math.max(1, refreshRateTicks); }
        public double regenerationCoefficient() { return refreshRateTicks / 20.0d; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        return value == null ? List.of() : List.of(String.valueOf(value));
    }
}
