package vn.svframe.mmocorefabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import vn.svframe.compat.YamlLite;
import vn.svframe.mmocorefabric.runtime.gameplay.ProfessionRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native Fabric adapters for MMOCore's alchemy/enchanting/smithing EXP sources. */
public final class MMOCoreSpecialProfessionExperienceMod implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("MMOCore-Fabric/SpecialProfessions");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOCore");
    private static final Map<String, Definition> DEFINITIONS = new ConcurrentHashMap<>();
    private static volatile long tick;
    private static volatile long lastStamp = Long.MIN_VALUE;

    @Override
    public void onInitialize() {
        reload(ROOT);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            if (tick % 100L == 0L) reloadIfChanged(ROOT);
        });
    }

    public static void awardEnchant(ServerPlayerEntity player, List<EnchantmentLevelEntry> entries) {
        if (player == null || entries == null || entries.isEmpty()) return;
        for (Definition definition : DEFINITIONS.values()) {
            for (SourceSpec source : definition.sources()) {
                if (!source.mechanic().equals("enchantitem")) continue;
                Set<String> filter = csv(source.param("enchant"));
                double total = 0.0;
                for (EnchantmentLevelEntry entry : entries) {
                    String enchant = enchantmentId(entry);
                    if (enchant.isEmpty() || (!filter.isEmpty() && !matchesToken(filter, enchant))) continue;
                    total += definition.enchantBase().getOrDefault(path(enchant), 0.0) * Math.max(0, entry.level);
                }
                grant(player, definition, source, total);
            }
        }
    }

    public static void awardRepair(ServerPlayerEntity player, ItemStack input, ItemStack output) {
        if (player == null || input == null || output == null || input.isEmpty() || output.isEmpty()) return;
        if (!input.isDamageable() || !output.isDamageable()) return;
        if (input.getMaxDamage() < 30 || output.getMaxDamage() < 10) return;
        int repaired = Math.max(0, input.getDamage() - output.getDamage());
        if (repaired <= 0) return;
        String material = Registries.ITEM.getId(output.getItem()).toString();
        for (Definition definition : DEFINITIONS.values()) {
            double base = definition.repairBase().getOrDefault(path(material), Double.NaN);
            if (!Double.isFinite(base)) continue;
            double factor = base * repaired / 100.0;
            for (SourceSpec source : definition.sources()) {
                if (!source.mechanic().equals("repairitem")) continue;
                if (!matchesType(source.param("type"), material)) continue;
                grant(player, definition, source, factor);
            }
        }
    }

    public static void awardBrew(ServerWorld world, BlockPos pos, ItemStack before, ItemStack after) {
        if (world == null || pos == null || before == null || after == null || before.isEmpty() || after.isEmpty()) return;
        PotionView oldPotion = PotionView.of(before);
        PotionView newPotion = PotionView.of(after);
        if (oldPotion == null || newPotion == null || oldPotion.equals(newPotion)) return;
        ServerPlayerEntity player = nearestPlayer(world, pos);
        if (player == null) return;

        for (Definition definition : DEFINITIONS.values()) {
            Alchemy alchemy = definition.alchemy();
            double base = alchemy.effects().getOrDefault(newPotion.effect(), Double.NaN);
            if (!Double.isFinite(base)) continue;
            for (SourceSpec source : definition.sources()) {
                if (!source.mechanic().equals("brewpotion")) continue;
                Set<String> filter = csv(source.param("effect"));
                if (!filter.isEmpty() && !matchesToken(filter, newPotion.effect())) continue;
                double exp = base;
                if (oldPotion.kind() == PotionKind.NORMAL && newPotion.kind() == PotionKind.SPLASH) exp *= alchemy.splash();
                if (oldPotion.kind() == PotionKind.NORMAL && newPotion.kind() == PotionKind.LINGERING) exp *= alchemy.lingering();
                if (!oldPotion.extended() && newPotion.extended()) exp *= alchemy.extend();
                if (!oldPotion.upgraded() && newPotion.upgraded()) exp *= alchemy.upgrade();
                exp *= source.number("multiplier", 1.0);
                grant(player, definition, source, exp);
            }
        }
    }

    private static void grant(ServerPlayerEntity player, Definition definition, SourceSpec source, double factor) {
        if (!(factor > 0.0) || !Double.isFinite(factor)) return;
        double amount = source.amount().roll() * factor;
        if (!(amount > 0.0) || !Double.isFinite(amount)) return;
        try {
            ProfessionRuntime.active().grant(player.getUuid(), definition.id(), source.mechanic(), amount, definition.curve()::required);
        } catch (IllegalStateException ignored) {
        }
    }

    private static ServerPlayerEntity nearestPlayer(ServerWorld world, BlockPos pos) {
        ServerPlayerEntity nearest = null;
        double nearestSq = 100.0;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        for (ServerPlayerEntity player : world.getPlayers()) {
            double distanceSq = player.squaredDistanceTo(x, y, z);
            if (distanceSq < nearestSq) {
                nearestSq = distanceSq;
                nearest = player;
            }
        }
        return nearest;
    }

    private static void reloadIfChanged(Path root) {
        try {
            long stamp = configStamp(root);
            if (stamp != lastStamp) reload(root);
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Could not inspect special profession configs", exception);
        }
    }

    private static void reload(Path root) {
        try {
            Map<String, List<String>> reusable = loadReusable(root.resolve("exp-sources.yml"));
            Map<String, Definition> next = new LinkedHashMap<>();
            Path professions = root.resolve("professions");
            if (Files.isDirectory(professions)) {
                try (var files = Files.walk(professions)) {
                    for (Path file : files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                            .sorted(Comparator.comparing(Path::toString)).toList()) {
                        Map<String, Object> yaml = YamlLite.map(YamlLite.parse(file));
                        String id = id(file);
                        List<SourceSpec> sources = new ArrayList<>();
                        for (String line : sourceLines(yaml.get("exp-sources"))) expand(line, reusable, new LinkedHashSet<>(), sources);
                        Curve curve = Curve.parse(root, string(yaml.get("exp-curve"), "levels"));
                        Map<String, Double> enchant = numericMap(map(yaml.get("base-enchant-exp")));
                        Map<String, Double> repair = numericMap(map(yaml.get("repair-exp")));
                        Alchemy alchemy = Alchemy.parse(map(yaml.get("alchemy-experience")));
                        next.put(id, new Definition(id, List.copyOf(sources), curve, enchant, repair, alchemy));
                    }
                }
            }
            DEFINITIONS.clear();
            DEFINITIONS.putAll(next);
            lastStamp = configStamp(root);
            LOG.info("Loaded brew/enchant/repair profession sources for " + DEFINITIONS.size() + " professions");
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Failed to reload brew/enchant/repair profession sources; keeping previous snapshot", exception);
        }
    }

    private static long configStamp(Path root) throws IOException {
        long stamp = 0L;
        Path professions = root.resolve("professions");
        if (Files.isDirectory(professions)) {
            try (var files = Files.walk(professions)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) stamp = Math.max(stamp, Files.getLastModifiedTime(file).toMillis());
            }
        }
        Path reusable = root.resolve("exp-sources.yml");
        if (Files.isRegularFile(reusable)) stamp = Math.max(stamp, Files.getLastModifiedTime(reusable).toMillis());
        return stamp;
    }

    private static Map<String, List<String>> loadReusable(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return Map.of();
        Map<String, Object> yaml = YamlLite.map(YamlLite.parse(file));
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : yaml.entrySet()) out.put(norm(entry.getKey()), sourceLines(entry.getValue()));
        return Map.copyOf(out);
    }

    private static void expand(String line, Map<String, List<String>> reusable, Set<String> stack, List<SourceSpec> out) {
        SourceSpec source = SourceSpec.parse(line);
        if (!source.mechanic().equals("from")) {
            out.add(source);
            return;
        }
        String name = norm(source.param("source"));
        if (name.isEmpty() || !stack.add(name)) return;
        for (String nested : reusable.getOrDefault(name, List.of())) expand(nested, reusable, stack, out);
        stack.remove(name);
    }

    private static List<String> sourceLines(Object value) {
        List<String> out = new ArrayList<>();
        collectStrings(value, out);
        return out;
    }

    private static void collectStrings(Object value, List<String> out) {
        if (value == null) return;
        if (value instanceof Collection<?> collection) for (Object item : collection) collectStrings(item, out);
        else if (value instanceof Map<?, ?> map) for (Object item : map.values()) collectStrings(item, out);
        else {
            String line = String.valueOf(value).trim();
            if (!line.isEmpty()) out.add(line);
        }
    }

    private static Map<String, Double> numericMap(Map<String, Object> source) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            try { out.put(norm(entry.getKey()), Double.parseDouble(String.valueOf(entry.getValue()))); }
            catch (NumberFormatException ignored) { }
        }
        return Map.copyOf(out);
    }

    private static String enchantmentId(EnchantmentLevelEntry entry) {
        return entry.enchantment.getKey().map(key -> key.getValue().toString()).orElse("");
    }

    private static Set<String> csv(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String value = norm(part);
            if (!value.isEmpty()) out.add(value);
        }
        return Set.copyOf(out);
    }

    private static boolean matchesToken(Set<String> filter, String actual) {
        String full = norm(actual), path = path(actual);
        return filter.contains(full) || filter.contains(path) || filter.contains(normPotionAlias(path));
    }

    private static boolean matchesType(String configured, String registryId) {
        if (configured == null || configured.isBlank()) return true;
        String expected = normId(configured), actual = normId(registryId);
        return actual.equals(expected) || path(actual).equals(path(expected));
    }

    private static String path(String id) {
        String value = norm(id);
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1);
    }

    private static String normId(String value) {
        String out = norm(value);
        return out.contains(":") ? out : "minecraft:" + out;
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String id(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return norm(dot > 0 ? name.substring(0, dot) : name);
    }

    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value).trim(); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }

    private static String normPotionAlias(String path) {
        String value = path;
        if (value.startsWith("long_")) value = value.substring(5);
        if (value.startsWith("strong_")) value = value.substring(7);
        return switch (value) {
            case "swiftness" -> "speed";
            case "leaping" -> "jump";
            case "healing" -> "instant_heal";
            case "harming" -> "instant_damage";
            case "regeneration" -> "regen";
            default -> value;
        };
    }

    private record Definition(String id, List<SourceSpec> sources, Curve curve,
                              Map<String, Double> enchantBase, Map<String, Double> repairBase, Alchemy alchemy) {}

    private record Alchemy(double splash, double lingering, double extend, double upgrade, Map<String, Double> effects) {
        static Alchemy parse(Map<String, Object> root) {
            Map<String, Object> special = map(root.get("special"));
            Map<String, Double> effects = numericMap(map(root.get("effects")));
            return new Alchemy(
                    1.0 + number(special.get("splash"), 0.0) / 100.0,
                    1.0 + number(special.get("lingering"), 0.0) / 100.0,
                    1.0 + number(special.get("extend"), 0.0) / 100.0,
                    1.0 + number(special.get("upgrade"), 0.0) / 100.0,
                    effects);
        }
        private static double number(Object value, double fallback) {
            if (value instanceof Number number) return number.doubleValue();
            try { return Double.parseDouble(String.valueOf(value)); }
            catch (Exception ignored) { return fallback; }
        }
    }

    private enum PotionKind { NORMAL, SPLASH, LINGERING }

    private record PotionView(PotionKind kind, String effect, boolean extended, boolean upgraded) {
        static PotionView of(ItemStack stack) {
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents == null || contents.potion().isEmpty()) return null;
            Identifier id = Registries.POTION.getId(contents.potion().get().value());
            if (id == null) return null;
            String path = id.getPath();
            boolean extended = path.startsWith("long_");
            boolean upgraded = path.startsWith("strong_");
            String effect = normPotionAlias(path);
            PotionKind kind = stack.isOf(Items.SPLASH_POTION) ? PotionKind.SPLASH
                    : stack.isOf(Items.LINGERING_POTION) ? PotionKind.LINGERING : PotionKind.NORMAL;
            return new PotionView(kind, effect, extended, upgraded);
        }
    }

    private record SourceSpec(String mechanic, Map<String, String> params, Amount amount) {
        static SourceSpec parse(String raw) {
            String line = raw == null ? "" : raw.trim();
            int brace = line.indexOf('{');
            String mechanic = norm(brace < 0 ? line : line.substring(0, brace));
            Map<String, String> params = new LinkedHashMap<>();
            if (brace >= 0) {
                int end = line.lastIndexOf('}');
                String body = end > brace ? line.substring(brace + 1, end) : line.substring(brace + 1);
                for (String token : splitParams(body)) {
                    int equals = token.indexOf('=');
                    if (equals <= 0) continue;
                    params.put(norm(token.substring(0, equals)), unquote(token.substring(equals + 1).trim()));
                }
            }
            return new SourceSpec(mechanic, Map.copyOf(params), Amount.parse(params.get("amount")));
        }
        String param(String key) { return params.get(norm(key)); }
        double number(String key, double fallback) {
            String value = param(key);
            if (value == null) return fallback;
            try { return Double.parseDouble(value); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        private static List<String> splitParams(String body) {
            List<String> out = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            char quote = 0;
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '\'' || c == '"') {
                    if (quote == 0) quote = c; else if (quote == c) quote = 0;
                    current.append(c);
                } else if (c == ';' && quote == 0) {
                    out.add(current.toString().trim()); current.setLength(0);
                } else current.append(c);
            }
            if (!current.isEmpty()) out.add(current.toString().trim());
            return out;
        }
        private static String unquote(String value) {
            if (value.length() >= 2) {
                char first = value.charAt(0), last = value.charAt(value.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }

    private record Amount(double min, double max) {
        static Amount parse(String raw) {
            if (raw == null || raw.isBlank()) return new Amount(1.0, 1.0);
            String value = raw.trim();
            int split = rangeSeparator(value);
            try {
                if (split > 0) {
                    double a = Double.parseDouble(value.substring(0, split).trim());
                    double b = Double.parseDouble(value.substring(split + 1).trim());
                    return new Amount(Math.min(a, b), Math.max(a, b));
                }
                double exact = Double.parseDouble(value);
                return new Amount(exact, exact);
            } catch (NumberFormatException ignored) {
                return new Amount(1.0, 1.0);
            }
        }
        double roll() { return min == max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max)); }
        private static int rangeSeparator(String value) {
            for (int i = 1; i < value.length() - 1; i++) if (value.charAt(i) == '-') return i;
            return -1;
        }
    }

    @FunctionalInterface
    private interface Curve {
        double required(int level);
        static Curve parse(Path root, String raw) throws IOException {
            String value = raw == null ? "levels" : raw.trim();
            Path file = resolveCurve(root, value);
            if (file != null && Files.isRegularFile(file)) {
                List<Double> levels = new ArrayList<>();
                for (String line : Files.readAllLines(file)) {
                    String clean = line.trim();
                    if (clean.isEmpty() || clean.startsWith("#")) continue;
                    levels.add(Double.parseDouble(clean));
                }
                if (!levels.isEmpty()) return level -> levels.get(Math.min(Math.max(level, 1), levels.size()) - 1);
            }
            NumericExpression expression = NumericExpression.compile(value.replace("{level}", "level"));
            return expression::eval;
        }
        private static Path resolveCurve(Path root, String value) {
            String clean = value.replace('\\', '/');
            if (!clean.matches("[A-Za-z0-9_.\\-/]+")) return null;
            Path direct = root.resolve(clean).normalize();
            if (Files.isRegularFile(direct)) return direct;
            if (!clean.toLowerCase(Locale.ROOT).endsWith(".txt")) {
                Path a = root.resolve("exp-curves").resolve(clean + ".txt").normalize();
                if (Files.isRegularFile(a)) return a;
                Path b = root.resolve("expcurves").resolve(clean + ".txt").normalize();
                if (Files.isRegularFile(b)) return b;
            }
            return null;
        }
    }

    private interface NumericExpression {
        double eval(double level);
        static NumericExpression compile(String source) { return new Parser(source == null || source.isBlank() ? "level * 100" : source).parse(); }
    }

    private static final class Parser {
        private final String source;
        private int index;
        private Parser(String source) { this.source = source; }
        NumericExpression parse() { NumericExpression value = expression(); ws(); if (index != source.length()) throw new IllegalArgumentException("Unexpected curve token: " + source.substring(index)); return value; }
        private NumericExpression expression() {
            NumericExpression left = term();
            while (true) { ws(); if (take('+')) { NumericExpression a=left,b=term(); left=l->a.eval(l)+b.eval(l); } else if (take('-')) { NumericExpression a=left,b=term(); left=l->a.eval(l)-b.eval(l); } else return left; }
        }
        private NumericExpression term() {
            NumericExpression left = power();
            while (true) { ws(); if (take('*')) { NumericExpression a=left,b=power(); left=l->a.eval(l)*b.eval(l); } else if (take('/')) { NumericExpression a=left,b=power(); left=l->a.eval(l)/b.eval(l); } else if (take('%')) { NumericExpression a=left,b=power(); left=l->a.eval(l)%b.eval(l); } else return left; }
        }
        private NumericExpression power() { NumericExpression left=unary(); ws(); if(!take('^'))return left; NumericExpression right=power(); return l->Math.pow(left.eval(l),right.eval(l)); }
        private NumericExpression unary() { ws(); if(take('+'))return unary(); if(take('-')){NumericExpression v=unary();return l->-v.eval(l);} return primary(); }
        private NumericExpression primary() {
            ws(); if(take('(')){NumericExpression v=expression();ws();if(!take(')'))throw new IllegalArgumentException("Missing ')' in curve " + source);return v;}
            if(source.regionMatches(true,index,"level",0,5)){index+=5;return l->l;}
            int start=index; while(index<source.length()&&(Character.isDigit(source.charAt(index))||source.charAt(index)=='.'))index++;
            if(start==index)throw new IllegalArgumentException("Expected number or level in curve " + source);
            double number=Double.parseDouble(source.substring(start,index));return l->number;
        }
        private void ws(){while(index<source.length()&&Character.isWhitespace(source.charAt(index)))index++;}
        private boolean take(char c){if(index<source.length()&&source.charAt(index)==c){index++;return true;}return false;}
    }
}
