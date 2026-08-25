package vn.svframe.mythiclibfabric.runtime.script;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptEngine {
    public record Definition(String id, boolean isPublic, List<String> conditions, List<String> mechanics) {
        public Definition {
            id = id.toLowerCase(Locale.ROOT);
            conditions = List.copyOf(conditions);
            mechanics = List.copyOf(mechanics);
        }
    }

    private final Map<String, Definition> defs = new ConcurrentHashMap<>();
    private final ExpressionRuntime expressions = new ExpressionRuntime();
    private final ScriptPlatform platform;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public ScriptEngine(ScriptPlatform platform) { this.platform = platform; }
    public void register(Definition definition) { defs.put(definition.id(), definition); }
    public Optional<Definition> find(String id) { return Optional.ofNullable(defs.get(norm(id))); }
    public boolean cast(String id, ScriptContext context) { return cast(id, context, 0); }
    public boolean cast(Definition definition, ScriptContext context) { return castDefinition(definition, context, 0); }

    private boolean cast(String id, ScriptContext context, int depth) {
        if (depth > 32) throw new IllegalStateException("script recursion");
        Definition definition = defs.get(norm(id));
        return definition != null && castDefinition(definition, context, depth);
    }

    private boolean castDefinition(Definition definition, ScriptContext context, int depth) {
        if (depth > 32) throw new IllegalStateException("script recursion");
        for (String raw : definition.conditions()) if (!condition(ScriptLineParser.parse(raw), context)) return false;
        for (String raw : definition.mechanics()) {
            if (context.cancelled()) break;
            mechanic(ScriptLineParser.parse(raw), context, depth);
        }
        return true;
    }

    private boolean condition(ScriptLineParser.Call call, ScriptContext context) {
        return switch (call.name()) {
            case "can_target", "cantarget" -> platform.canTarget(context.caster(), context.target(), call.params().getOrDefault("interaction_type", "OFFENSE_ACTION"));
            case "chance" -> Math.random() < number(call, "c", number(call, "chance", 1, context), context);
            case "bool", "boolean" -> expressions.evaluateBoolean(call.params().getOrDefault("expr", call.params().getOrDefault("formula", "0")), context.numbers());
            case "variable_exists" -> context.numbers().containsKey(call.params().getOrDefault("var", call.params().getOrDefault("variable", ""))) || context.vectors().containsKey(call.params().getOrDefault("var", call.params().getOrDefault("variable", "")));
            case "has_damage_type" -> {
                String raw = call.params().getOrDefault("types", "");
                yield Arrays.stream(raw.split("[,;]")).map(String::trim).anyMatch(type -> context.damageTypes().contains(type.toUpperCase(Locale.ROOT)));
            }
            case "ammo" -> platform.hasAmmo(context.caster(), (int) number(call, "amount", 1, context));
            case "cooldown" -> ready(context.caster(), call.params().getOrDefault("path", "default"), System.currentTimeMillis());
            default -> true;
        };
    }

    private void mechanic(ScriptLineParser.Call call, ScriptContext context, int depth) {
        Map<String, String> params = call.params();
        switch (call.name()) {
            case "set_double", "set_int", "set_integer" -> {
                String key = params.getOrDefault("variable", params.getOrDefault("var", "var"));
                context.numbers().put(key, number(call, params.containsKey("value") ? "value" : params.containsKey("val") ? "val" : "amount", 0, context));
            }
            case "multiply_damage" -> {
                double amount = number(call, params.containsKey("amount") ? "amount" : "scalar", 1, context);
                String type = params.getOrDefault("type", params.getOrDefault("damage_type", "")).trim();
                String element = params.getOrDefault("element", "").trim();
                if (!element.isEmpty() && context.damageBridge() != null) context.damageBridge().multiplyElement(element, amount);
                else if (!type.isEmpty() && context.damageBridge() != null) context.damageBridge().multiplyType(type, amount);
                else if (context.damageBridge() != null) context.damageBridge().multiplyAll(amount);
                else context.damage(context.damage() * amount);
            }
            case "additive_damage", "add_damage_modifier" -> {
                double amount = number(call, params.containsKey("amount") ? "amount" : "modifier", 0, context);
                String type = params.getOrDefault("type", params.getOrDefault("damage_type", "")).trim();
                if (!type.isEmpty() && context.damageBridge() != null) context.damageBridge().additiveType(type, amount);
                else if (context.damageBridge() != null) context.damageBridge().additiveAll(amount);
            }
            case "damage" -> platform.damage(context.target(), number(call, "amount", context.damage(), context), params.getOrDefault("damage_type", params.getOrDefault("dtype", "")));
            case "heal" -> platform.heal(target(params, context), number(call, "amount", 1, context));
            case "particle", "spawn_particle" -> platform.particle(target(params, context), params.getOrDefault("particle", "CRIT"), (int) number(call, "amount", 1, context), number(call, "x", number(call, "offset_x", 0, context), context), number(call, "y", number(call, "offset_y", 0, context), context), number(call, "z", number(call, "offset_z", 0, context), context), number(call, "speed", 0, context));
            case "sound", "play_sound" -> platform.sound(target(params, context), params.getOrDefault("sound", params.getOrDefault("s", "")), (float) number(call, "volume", 1, context), (float) number(call, "pitch", 1, context));
            case "potion" -> platform.potion(target(params, context), params.getOrDefault("effect", params.getOrDefault("type", "SLOW")), (int) number(call, "level", 1, context), (int) number(call, "duration", 20, context));
            case "set_on_fire" -> platform.setOnFire(target(params, context), (int) number(call, "ticks", 20, context));
            case "cancel_event" -> context.cancel();
            case "set_no_damage_ticks" -> platform.noDamageTicks(target(params, context), (int) number(call, "ticks", 10, context));
            case "action_bar" -> platform.actionBar(target(params, context), params.getOrDefault("m", params.getOrDefault("message", "")), (int) number(call, "priority", 0, context), (int) number(call, "duration", 20, context));
            case "script", "cast" -> {
                String name = params.getOrDefault("name", params.getOrDefault("s", ""));
                int iterations = (int) number(call, "iterations", 1, context);
                String counter = params.get("counter");
                for (int i = 1; i <= Math.max(1, iterations); i++) {
                    if (counter != null) context.numbers().put(counter, (double) i);
                    cast(name, context, depth + 1);
                }
            }
            case "apply_cooldown" -> cooldown(context.caster(), params.getOrDefault("path", "default"), System.currentTimeMillis() + (long) (number(call, "amount", 1, context) * 50));
            case "set_vector" -> context.vectors().put(params.getOrDefault("variable", "vector"), new Vector3(number(call, "x", 0, context), number(call, "y", 0, context), number(call, "z", 0, context)));
            case "copy_vector" -> context.vectors().put(params.getOrDefault("variable", "vector"), vectorValue(params.getOrDefault("value", ""), context));
            case "add_vector" -> {
                String key = params.getOrDefault("variable", "vector");
                Vector3 current = context.vectors().getOrDefault(key, new Vector3(0, 0, 0));
                Vector3 added = params.containsKey("added") ? context.vectors().getOrDefault(params.get("added"), new Vector3(0, 0, 0)) : new Vector3(number(call, "x", 0, context), number(call, "y", 0, context), number(call, "z", 0, context));
                context.vectors().put(key, current.add(added));
            }
            case "subtract_vector", "sub_vector", "sub_vec" -> {
                String key = params.getOrDefault("variable", "vector");
                String value = params.getOrDefault("value", params.getOrDefault("subtracted", ""));
                context.vectors().put(key, context.vectors().getOrDefault(key, new Vector3(0, 0, 0)).subtract(vectorValue(value, context)));
            }
            case "normalize_vector" -> context.vectors().computeIfPresent(params.getOrDefault("variable", "vector"), (key, value) -> value.normalize());
            case "multiply_vector" -> {
                String key = params.getOrDefault("variable", "vector");
                double amount = number(call, "coef", number(call, "scalar", 1, context), context);
                context.vectors().computeIfPresent(key, (ignored, value) -> value.multiply(amount));
            }
            case "set_y" -> {
                String key = params.getOrDefault("variable", "vector");
                double y = number(call, "y", 0, context);
                context.vectors().computeIfPresent(key, (ignored, value) -> value.withY(y));
            }
            case "orient_vector" -> {
                String key = params.getOrDefault("variable", "vector"), axis = params.getOrDefault("axis", "");
                Vector3 value = context.vectors().get(key), reference = context.vectors().get(axis);
                if (value != null && reference != null) context.vectors().put(key, orient(value, reference));
            }
            case "incr" -> context.numbers().merge(params.getOrDefault("variable", params.getOrDefault("var", "var")), number(call, "amount", number(call, "value", 1, context), context), Double::sum);
            case "set_velocity" -> {
                Vector3 vector = context.vectors().get(params.getOrDefault("vector", "vector"));
                if (vector != null) platform.velocity(target(params, context), vector);
            }
            case "call_trigger" -> platform.trigger(context.caster(), params.getOrDefault("trigger", ""), context);
            case "mark_crit" -> {
                context.objects().put("critical", true);
                String raw = params.getOrDefault("dtype", "");
                for (String type : raw.split("[,;]")) if (!type.isBlank()) context.damageTypes().add("CRIT_" + type.trim().toUpperCase(Locale.ROOT));
            }
            case "entity_effect" -> platform.entityEffect(target(params, context), params.getOrDefault("effect", "HURT"));
            case "remove_potion" -> platform.removePotion(target(params, context), params.getOrDefault("effect", params.getOrDefault("type", "")));
            case "take_ammo" -> platform.takeAmmo(context.caster(), (int) number(call, "amount", 1, context));
            case "shoot_arrow" -> platform.shootArrow(context.caster(), number(call, "speed", 3, context), number(call, "damage", context.damage(), context));
            case "shulker_bullet" -> platform.shulkerBullet(context.caster(), context.target(), number(call, "damage", context.damage(), context));
            case "helix" -> {
                double radius = number(call, "radius", 1, context), height = number(call, "height", 2, context);
                int points = (int) number(call, "points", 48, context);
                String tick = params.getOrDefault("tick", "");
                Vector3 base = resolveTargetLocation(params, context);
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / Math.max(1, points), y = height * i / Math.max(1, points);
                    ScriptContext nested = context.copy();
                    nested.targetLocation(base.add(new Vector3(Math.cos(angle) * radius, y, Math.sin(angle) * radius)));
                    if (!tick.isBlank()) cast(tick, nested, depth + 1);
                }
            }
            case "sphere" -> {
                double radius = number(call, "radius", 1, context);
                int points = (int) number(call, "points", 64, context);
                String tick = params.getOrDefault("tick", "");
                Vector3 base = resolveTargetLocation(params, context);
                for (int i = 0; i < points; i++) {
                    double phi = Math.acos(1 - 2 * (i + .5) / points), theta = Math.PI * (1 + Math.sqrt(5)) * i;
                    ScriptContext nested = context.copy();
                    nested.targetLocation(base.add(new Vector3(Math.cos(theta) * Math.sin(phi) * radius, Math.cos(phi) * radius, Math.sin(theta) * Math.sin(phi) * radius)));
                    if (!tick.isBlank()) cast(tick, nested, depth + 1);
                }
            }
            case "draw_line" -> {
                Vector3 from = platform.location(context.caster()), to = resolveTargetLocation(params, context);
                String tick = params.getOrDefault("tick", params.getOrDefault("script", ""));
                int points = Math.max(1, (int) number(call, "points", 20, context));
                for (int i = 0; i <= points; i++) {
                    double ratio = i / (double) points;
                    ScriptContext nested = context.copy();
                    nested.targetLocation(from.multiply(1 - ratio).add(to.multiply(ratio)));
                    if (!tick.isBlank()) cast(tick, nested, depth + 1);
                }
            }
            case "raytrace", "ray_trace", "projectile" -> {
                Vector3 origin = platform.location(context.caster()), direction = platform.eyeDirection(context.caster()).normalize();
                double speed = number(call, "speed", 2, context), range = number(call, "range", number(call, "life_span", 20, context) * speed / 20d, context), size = number(call, "size", .2, context);
                int life = (int) number(call, "life_span", 20, context);
                String tick = params.getOrDefault("tick", ""), hit = params.getOrDefault("hit_entity", "");
                platform.projectile(new ScriptPlatform.ProjectileSpec(origin, direction, speed, range, size, life), location -> {
                    if (!tick.isBlank()) { ScriptContext nested = context.copy(); nested.targetLocation(location); cast(tick, nested, depth + 1); }
                }, entity -> {
                    if (!hit.isBlank()) { ScriptContext nested = context.copy(); nested.target(entity); cast(hit, nested, depth + 1); }
                }, () -> {});
            }
            default -> { }
        }
    }

    private Vector3 resolveTargetLocation(Map<String, String> params, ScriptContext context) {
        if (context.targetLocation() != null) return context.targetLocation();
        String target = params.getOrDefault("target", params.getOrDefault("target_location", ""));
        if (target.equalsIgnoreCase("caster")) return platform.location(context.caster());
        return context.target() != null ? platform.location(context.target()) : platform.location(context.caster());
    }

    private static Vector3 orient(Vector3 value, Vector3 axis) {
        Vector3 z = axis.normalize();
        Vector3 up = Math.abs(z.y()) > .99 ? new Vector3(1, 0, 0) : new Vector3(0, 1, 0);
        Vector3 x = cross(up, z).normalize();
        Vector3 y = cross(z, x).normalize();
        return x.multiply(value.x()).add(y.multiply(value.y())).add(z.multiply(value.z()));
    }

    private static Vector3 cross(Vector3 a, Vector3 b) { return new Vector3(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x()); }

    private Vector3 vectorValue(String value, ScriptContext context) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "caster.location" -> platform.location(context.caster());
            case "target.location" -> platform.location(context.target());
            case "caster.eye_direction" -> platform.eyeDirection(context.caster());
            default -> context.vectors().getOrDefault(value, new Vector3(0, 0, 0));
        };
    }

    private UUID target(Map<String, String> params, ScriptContext context) {
        String target = params.getOrDefault("target", "").toLowerCase(Locale.ROOT);
        return target.equals("caster") ? context.caster() : context.target() != null ? context.target() : context.caster();
    }

    private double number(ScriptLineParser.Call call, String key, double fallback, ScriptContext context) {
        String expression = call == null ? null : call.params().get(key);
        if (expression == null) return fallback;
        try {
            Map<String, Double> variables = new HashMap<>(context.numbers());
            for (var entry : context.numbers().entrySet()) variables.put("var." + entry.getKey(), entry.getValue());
            variables.put("attack.damage", context.damage());
            return expressions.evaluate(expression, variables);
        } catch (Exception ignored) {
            try { return Double.parseDouble(expression); } catch (Exception ignoredAgain) { return fallback; }
        }
    }

    private boolean ready(UUID uuid, String path, long now) { return now >= cooldowns.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>()).getOrDefault(path, 0L); }
    private void cooldown(UUID uuid, String path, long until) { cooldowns.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>()).put(path, until); }
    private static String norm(String value) { return value.trim().toLowerCase(Locale.ROOT); }
}
