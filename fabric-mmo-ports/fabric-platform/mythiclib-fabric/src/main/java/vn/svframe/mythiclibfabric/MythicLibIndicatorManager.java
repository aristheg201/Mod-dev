package vn.svframe.mythiclibfabric;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import vn.svframe.compat.YamlLite;
import vn.svframe.mythiclibfabric.mixin.DisplayEntityAccessor;
import vn.svframe.mythiclibfabric.mixin.TextDisplayEntityAccessor;
import vn.svframe.mythiclibfabric.runtime.DamagePacket;
import vn.svframe.mythiclibfabric.runtime.DamageType;
import vn.svframe.mythiclibfabric.runtime.NativeDamageMetadata;
import vn.svframe.mythiclibfabric.runtime.NativeElementRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/** Native Fabric/TextDisplay port of MythicLib 1.7.1 damage indicators. */
public final class MythicLibIndicatorManager {
    private enum GroupMode { SINGLE, TYPE, PACKET }

    private record Icon(String normal, String crit) {
        String value(boolean critical) { return critical ? crit : normal; }
    }

    private record Indicator(double value, Collection<DamageType> types, String element) { }

    private record Active(DisplayEntity.TextDisplayEntity entity, Vec3d velocity, double gravity,
                          boolean move, int period, long expiresAt) { }

    private static final Path CONFIG = MythicLibFabricMod.configRoot().resolve("indicators.yml");
    private static final List<Active> ACTIVE = new CopyOnWriteArrayList<>();
    private static final Map<DamageType, Icon> ICONS = new EnumMap<>(DamageType.class);
    private static volatile boolean enabled = true;
    private static volatile double minDamage = 0.1d;
    private static volatile String rawFormat = "{icon} &f{value}";
    private static volatile DecimalFormat decimals = decimal("0.#");
    private static volatile GroupMode groupMode = GroupMode.PACKET;
    private static volatile List<DamageType> typeSplits = List.of(DamageType.WEAPON, DamageType.SKILL);
    private static volatile String iconJoin = "";
    private static volatile boolean splitHolograms = true;
    private static volatile String hologramJoin = " ";
    private static volatile boolean move = true;
    private static volatile double radialVelocity = 1.0d;
    private static volatile double gravity = 1.0d;
    private static volatile double initialUpwardVelocity = 1.0d;
    private static volatile double yOffset = 0.1d;
    private static volatile double entityHeightPercent = 0.75d;
    private static volatile double rOffset = 0.5d;
    private static volatile double entityWidthPercent = 0.75d;
    private static volatile int tickPeriod = 3;
    private static volatile int lifespan = 20;

    private MythicLibIndicatorManager() { }

    public static boolean reload() {
        try {
            if (!Files.isRegularFile(CONFIG)) return false;
            Map<String, Object> root = YamlLite.map(YamlLite.parse(CONFIG));
            Map<String, Object> section = map(root.get("damage_indicators"));
            if (section.isEmpty()) return false;

            enabled = bool(section.get("enabled"), true);
            minDamage = number(section.get("min_damage"), 0.1d);
            rawFormat = string(section.get("format"), "{icon} &f{value}");
            decimals = decimal(string(section.get("decimal_format"), "0.#"));
            groupMode = enumValue(GroupMode.class, section.get("group_by"), GroupMode.PACKET);
            typeSplits = damageTypes(section.get("damage_type_splits"), List.of(DamageType.WEAPON, DamageType.SKILL));
            iconJoin = string(section.get("damage_type_icon_join"), "");
            splitHolograms = bool(section.get("split_holograms"), true);
            hologramJoin = string(section.get("holograms_join"), " ");
            move = bool(section.get("move"), true);
            radialVelocity = number(section.get("radial_velocity"), 1.0d);
            gravity = number(section.get("gravity"), 1.0d);
            initialUpwardVelocity = number(section.get("initial_upward_velocity"), 1.0d);
            yOffset = number(section.get("y_offset"), 0.1d);
            entityHeightPercent = number(section.get("entity_height_percent"), 0.75d);
            rOffset = number(section.get("r_offset"), 0.5d);
            entityWidthPercent = number(first(section, "entity_width_percent", "entity_radius_percent"), 0.75d);
            tickPeriod = Math.max(1, integer(section.get("tick_period"), 3));
            lifespan = Math.max(1, integer(section.get("lifespan"), 20));

            ICONS.clear();
            Map<String, Object> icons = map(section.get("icon"));
            for (DamageType type : DamageType.values()) {
                Object raw = icons.get(type.name().toLowerCase(Locale.ROOT));
                Icon icon = icon(raw);
                if (icon != null) ICONS.put(type, icon);
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public static void damage(LivingEntity target, NativeDamageMetadata metadata) {
        if (!enabled || target == null || metadata == null || target.getWorld().isClient()) return;
        if (!(target.getWorld() instanceof ServerWorld world)) return;

        List<Indicator> indicators = indicators(metadata);
        indicators.removeIf(indicator -> indicator.value() < minDamage);
        if (indicators.isEmpty()) return;

        if (splitHolograms) {
            for (Indicator indicator : indicators) spawn(world, target, format(metadata, indicator));
        } else {
            List<String> lines = new ArrayList<>(indicators.size());
            for (Indicator indicator : indicators) lines.add(format(metadata, indicator));
            spawn(world, target, String.join(hologramJoin, lines));
        }
    }

    public static void tick(long tick) {
        for (Active active : ACTIVE) {
            DisplayEntity.TextDisplayEntity entity = active.entity();
            if (entity.isRemoved() || tick >= active.expiresAt()) {
                entity.discard();
                ACTIVE.remove(active);
                continue;
            }
            if (!active.move() || tick % active.period() != 0L) continue;
            double dt = active.period() / 20.0d;
            Vec3d velocity = active.velocity();
            entity.setPosition(entity.getX() + velocity.x * dt,
                    entity.getY() + velocity.y * dt,
                    entity.getZ() + velocity.z * dt);
            Vec3d next = velocity.add(0.0d, -active.gravity() * dt, 0.0d);
            ACTIVE.set(ACTIVE.indexOf(active), new Active(entity, next, active.gravity(), true, active.period(), active.expiresAt()));
        }
    }

    public static void clear() {
        for (Active active : ACTIVE) if (!active.entity().isRemoved()) active.entity().discard();
        ACTIVE.clear();
    }

    private static List<Indicator> indicators(NativeDamageMetadata metadata) {
        List<Indicator> result = new ArrayList<>();
        switch (groupMode) {
            case SINGLE -> result.add(new Indicator(metadata.damage(), metadata.collectTypes(), null));
            case TYPE -> {
                for (DamageType type : typeSplits) result.add(new Indicator(metadata.damage(type), List.of(type), null));
            }
            case PACKET -> {
                for (DamagePacket packet : metadata.packets())
                    result.add(new Indicator(packet.getFinalValue(), packet.getTypes(), packet.getElement()));
            }
        }
        return result;
    }

    private static String format(NativeDamageMetadata metadata, Indicator indicator) {
        boolean critical = isCritical(metadata, indicator);
        String value;
        synchronized (MythicLibIndicatorManager.class) { value = decimals.format(indicator.value()); }
        return rawFormat.replace("{icon}", icon(indicator, critical)).replace("{value}", value);
    }

    private static boolean isCritical(NativeDamageMetadata metadata, Indicator indicator) {
        if (indicator.types().contains(DamageType.WEAPON) && metadata.isWeaponCriticalStrike()) return true;
        if (indicator.types().contains(DamageType.SKILL) && metadata.isSkillCriticalStrike()) return true;
        return indicator.element() != null && metadata.isElementalCriticalStrike(indicator.element());
    }

    private static String icon(Indicator indicator, boolean critical) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (DamageType type : indicator.types()) {
            Icon icon = ICONS.get(type);
            if (icon == null) continue;
            if (!first) builder.append(iconJoin);
            first = false;
            builder.append(icon.value(critical));
        }
        if (indicator.element() != null) {
            NativeElementRegistry.Element element = MythicLibCombatRuntime.elements().get(indicator.element());
            if (element != null) {
                if (!first) builder.append(iconJoin);
                builder.append(element.color()).append(element.loreIcon());
            }
        }
        return builder.toString();
    }

    private static void spawn(ServerWorld world, LivingEntity target, String formatted) {
        DisplayEntity.TextDisplayEntity display = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        ((TextDisplayEntityAccessor) (Object) display).mythiclib$setText(legacyText(formatted));
        ((DisplayEntityAccessor) (Object) display).mythiclib$setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        display.setNoGravity(true);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2.0d);
        double radius = rOffset + target.getWidth() * entityWidthPercent * random.nextDouble();
        double x = target.getX() + Math.cos(angle) * radius;
        double y = target.getY() + yOffset + target.getHeight() * entityHeightPercent;
        double z = target.getZ() + Math.sin(angle) * radius;
        display.setPosition(x, y, z);
        if (!world.spawnEntity(display)) return;

        Vec3d velocity = move
                ? new Vec3d(Math.cos(angle) * radialVelocity, initialUpwardVelocity, Math.sin(angle) * radialVelocity)
                : Vec3d.ZERO;
        ACTIVE.add(new Active(display, velocity, gravity, move, tickPeriod, MythicLibFabricMod.currentTick() + lifespan));
    }

    private static Text legacyText(String input) {
        MutableText root = Text.empty();
        StringBuilder segment = new StringBuilder();
        List<Formatting> active = new ArrayList<>();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < input.length()) {
                Formatting formatting = formatting(input.charAt(i + 1));
                if (formatting != null) {
                    append(root, segment, active);
                    i++;
                    if (formatting == Formatting.RESET || formatting.isColor()) active.clear();
                    if (formatting != Formatting.RESET) active.add(formatting);
                    continue;
                }
            }
            segment.append(c);
        }
        append(root, segment, active);
        return root;
    }

    private static void append(MutableText root, StringBuilder segment, List<Formatting> formatting) {
        if (segment.isEmpty()) return;
        MutableText part = Text.literal(segment.toString());
        if (!formatting.isEmpty()) part.formatted(formatting.toArray(Formatting[]::new));
        root.append(part);
        segment.setLength(0);
    }

    private static Formatting formatting(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> Formatting.BLACK; case '1' -> Formatting.DARK_BLUE; case '2' -> Formatting.DARK_GREEN;
            case '3' -> Formatting.DARK_AQUA; case '4' -> Formatting.DARK_RED; case '5' -> Formatting.DARK_PURPLE;
            case '6' -> Formatting.GOLD; case '7' -> Formatting.GRAY; case '8' -> Formatting.DARK_GRAY;
            case '9' -> Formatting.BLUE; case 'a' -> Formatting.GREEN; case 'b' -> Formatting.AQUA;
            case 'c' -> Formatting.RED; case 'd' -> Formatting.LIGHT_PURPLE; case 'e' -> Formatting.YELLOW;
            case 'f' -> Formatting.WHITE; case 'k' -> Formatting.OBFUSCATED; case 'l' -> Formatting.BOLD;
            case 'm' -> Formatting.STRIKETHROUGH; case 'n' -> Formatting.UNDERLINE; case 'o' -> Formatting.ITALIC;
            case 'r' -> Formatting.RESET; default -> null;
        };
    }

    private static Icon icon(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object normal = map.get("normal"); Object crit = map.get("crit");
            String n = normal == null ? "" : String.valueOf(normal);
            return new Icon(n, crit == null ? n : String.valueOf(crit));
        }
        if (raw == null) return null;
        String value = String.valueOf(raw); return new Icon(value, value);
    }

    private static List<DamageType> damageTypes(Object raw, List<DamageType> fallback) {
        if (!(raw instanceof List<?> list)) return fallback;
        List<DamageType> result = new ArrayList<>();
        for (Object value : list) {
            try { result.add(DamageType.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ignored) { }
        }
        return result.isEmpty() ? fallback : List.copyOf(result);
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object raw) {
        return raw instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
    }
    private static Object first(Map<String, Object> map, String... keys) { for (String key : keys) if (map.containsKey(key)) return map.get(key); return null; }
    private static boolean bool(Object raw, boolean fallback) { return raw == null ? fallback : raw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(raw)); }
    private static double number(Object raw, double fallback) { try { return raw instanceof Number n ? n.doubleValue() : raw == null ? fallback : Double.parseDouble(String.valueOf(raw)); } catch (NumberFormatException ignored) { return fallback; } }
    private static int integer(Object raw, int fallback) { try { return raw instanceof Number n ? n.intValue() : raw == null ? fallback : Integer.parseInt(String.valueOf(raw)); } catch (NumberFormatException ignored) { return fallback; } }
    private static String string(Object raw, String fallback) { return raw == null ? fallback : String.valueOf(raw); }
    private static <E extends Enum<E>> E enumValue(Class<E> type, Object raw, E fallback) { try { return raw == null ? fallback : Enum.valueOf(type, String.valueOf(raw).trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { return fallback; } }
    private static DecimalFormat decimal(String pattern) { DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US); return new DecimalFormat(pattern, symbols); }
}
