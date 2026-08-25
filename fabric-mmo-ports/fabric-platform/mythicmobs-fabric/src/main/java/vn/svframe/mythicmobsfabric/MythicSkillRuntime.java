package vn.svframe.mythicmobsfabric;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Executes legacy MythicMobs skill YAML directly against Fabric server entities. */
final class MythicSkillRuntime {
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final int MAX_RECURSION = 32;

    private MythicSkillRuntime() {}

    static boolean cast(String id, UUID casterId, UUID targetId, Map<String, ?> parameters) {
        return cast(id, casterId, targetId, parameters, 0);
    }

    private static boolean cast(String id, UUID casterId, UUID targetId, Map<String, ?> parameters, int depth) {
        if (depth > MAX_RECURSION) return false;
        Map<String, Object> definition = MythicMobsFabricMod.skillDefinition(id);
        if (definition == null) return false;

        Entity caster = entity(casterId);
        Entity target = entity(targetId == null ? casterId : targetId);
        if (caster == null) return false;

        CastContext context = new CastContext(id, caster, target == null ? caster : target,
                parameters == null ? Map.of() : Map.copyOf(parameters), depth);

        if (!conditionsPass(strings(get(definition, "Conditions")), context)) return false;

        double cooldown = number(get(definition, "Cooldown"), 0.0d);
        if (cooldown > 0.0d) {
            String key = caster.getUuid() + ":" + normalize(id);
            long now = System.currentTimeMillis();
            long until = COOLDOWNS.getOrDefault(key, 0L);
            if (until > now) return false;
            COOLDOWNS.put(key, now + Math.max(1L, Math.round(cooldown * 1000.0d)));
        }

        List<String> lines = strings(get(definition, "Skills"));
        if (lines.isEmpty()) return false;
        executeFrom(lines, 0, context);
        return true;
    }

    private static void executeFrom(List<String> lines, int index, CastContext context) {
        for (int i = index; i < lines.size(); i++) {
            ParsedLine line = parse(lines.get(i));
            if (line == null) continue;

            if (line.mechanic.equals("delay")) {
                int delay = (int) Math.max(0, number(line.freeArgument, number(line.args.get("ticks"), number(line.args.get("t"), 0.0d))));
                int next = i + 1;
                MythicMobsFabricMod.schedule(delay, () -> executeFrom(lines, next, context));
                return;
            }

            if (!inlineConditionsPass(line.inlineConditions, context)) continue;
            Collection<Entity> targets = resolveTargets(line.targeter, line.targetArgs, context);
            if (targets.isEmpty()) targets = List.of(context.target);
            for (Entity target : targets) executeMechanic(line, context, target);
        }
    }

    private static boolean executeMechanic(ParsedLine line, CastContext context, Entity target) {
        String mechanic = line.mechanic;
        Map<String, String> args = line.args;
        return switch (mechanic) {
            case "message", "msg" -> message(context, target, first(args, "m", "message", "msg", "text"));
            case "randommessage" -> randomMessage(context, target, first(args, "m", "messages", "message"));
            case "damage", "damagephysical", "damagebase" -> damage(target, number(first(args, "amount", "a", "damage", "d"), 1.0d));
            case "damagepercent" -> damagePercent(target, number(first(args, "percent", "p", "amount", "a"), 0.0d));
            case "heal" -> heal(target, number(first(args, "amount", "a", "heal", "h"), 1.0d));
            case "healpercent" -> healPercent(target, number(first(args, "percent", "p", "amount", "a"), 0.0d));
            case "sethealth" -> setHealth(target, number(first(args, "amount", "a", "health", "h"), 1.0d));
            case "potion", "potionadd" -> potion(target,
                    first(args, "type", "t", "effect", "e"),
                    (int) number(first(args, "duration", "d", "ticks"), 100.0d),
                    (int) number(first(args, "level", "lvl", "l", "amplifier", "a"), 1.0d));
            case "potionclear", "clearpotion", "removeallpotion" -> clearPotions(target);
            case "removepotion" -> removePotion(target, first(args, "type", "t", "effect", "e"));
            case "ignite", "setonfire" -> ignite(target, (int) number(first(args, "ticks", "t", "duration", "d"), 100.0d));
            case "extinguish" -> extinguish(target);
            case "velocity" -> velocity(target,
                    number(first(args, "x", "vx"), target.getVelocity().x),
                    number(first(args, "y", "vy"), target.getVelocity().y),
                    number(first(args, "z", "vz"), target.getVelocity().z));
            case "throw" -> throwTarget(context.caster, target,
                    number(first(args, "velocity", "v", "horizontal", "h"), 1.0d),
                    number(first(args, "velocityy", "vy", "y"), 0.2d));
            case "pull" -> pull(context.caster, target,
                    number(first(args, "velocity", "v", "amount", "a"), 1.0d));
            case "leap" -> leap(target,
                    number(first(args, "velocity", "v", "forward", "f"), 1.0d),
                    number(first(args, "velocityy", "vy", "y"), 0.2d));
            case "teleport", "teleportto" -> teleport(target, context.target);
            case "sound", "playsound" -> sound(target,
                    first(args, "sound", "s"),
                    (float) number(first(args, "volume", "v"), 1.0d),
                    (float) number(first(args, "pitch", "p"), 1.0d));
            case "particles", "particle", "particlebox", "particlering" -> particles(target,
                    first(args, "particle", "p"),
                    (int) number(first(args, "amount", "a", "count", "c"), 1.0d),
                    number(first(args, "hspread", "hs", "spread", "s"), 0.0d),
                    number(first(args, "yspread", "ys"), 0.0d),
                    number(first(args, "speed", "sp"), 0.0d),
                    number(first(args, "yoffset", "yo"), 0.5d));
            case "summon" -> summon(context, target,
                    first(args, "mob", "m", "type", "t"),
                    (int) number(first(args, "amount", "a"), 1.0d),
                    number(first(args, "noise", "n", "radius", "r"), 0.0d),
                    (int) number(first(args, "level", "l"), 1.0d));
            case "remove" -> remove(target);
            case "kill" -> kill(target);
            case "setname", "setdisplay" -> setName(context, target, first(args, "name", "n", "display", "d"));
            case "setgravity", "setusegravity" -> setGravity(target, bool(first(args, "gravity", "g", "value", "v"), true));
            case "feed" -> feed(target, (int) number(first(args, "amount", "a", "food", "f"), 1.0d));
            case "command", "consolecommand" -> command(context, first(args, "command", "cmd", "c"), mechanic.equals("consolecommand"));
            case "skill", "metaskill" -> nestedSkill(context, first(args, "skill", "s", "name", "n"), target);
            default -> false;
        };
    }

    private static boolean conditionsPass(List<String> conditions, CastContext context) {
        for (String raw : conditions) if (!condition(raw, context, context.target)) return false;
        return true;
    }

    private static boolean inlineConditionsPass(List<String> conditions, CastContext context) {
        for (String raw : conditions) if (!condition(raw, context, context.target)) return false;
        return true;
    }

    private static boolean condition(String raw, CastContext context, Entity target) {
        ParsedComponent component = parseComponent(raw);
        if (component == null) return true;
        String name = component.name;
        Map<String, String> args = component.args;
        boolean negate = false;
        if (name.startsWith("!")) {
            negate = true;
            name = name.substring(1);
        }
        boolean result = switch (name) {
            case "targetwithin", "distance" -> distance(context.caster, target) <= number(first(args, "distance", "d", "value", "v"), 0.0d);
            case "targetnotwithin" -> distance(context.caster, target) > number(first(args, "distance", "d", "value", "v"), 0.0d);
            case "onground" -> target.isOnGround();
            case "offground" -> !target.isOnGround();
            case "player", "isplayer" -> target instanceof ServerPlayerEntity;
            case "living" -> target instanceof LivingEntity;
            case "alive" -> target instanceof LivingEntity living && living.isAlive();
            case "dead" -> target instanceof LivingEntity living && !living.isAlive();
            case "haspermission", "permission" -> target instanceof ServerPlayerEntity player
                    && player.hasPermissionLevel((int) number(first(args, "level", "l"), 0.0d));
            case "health" -> target instanceof LivingEntity living
                    && compare(living.getHealth(), first(args, "health", "h", "value", "v"));
            case "healthpercent" -> target instanceof LivingEntity living
                    && compare(living.getMaxHealth() <= 0 ? 0.0d : living.getHealth() * 100.0d / living.getMaxHealth(), first(args, "percent", "p", "value", "v"));
            default -> true;
        };
        return negate ? !result : result;
    }

    private static Collection<Entity> resolveTargets(String targeter, Map<String, String> args, CastContext context) {
        String name = normalize(targeter);
        if (name.isEmpty()) return List.of(context.target);
        if (name.startsWith("@")) name = name.substring(1);
        return switch (name) {
            case "self", "caster" -> List.of(context.caster);
            case "target", "trigger" -> List.of(context.target);
            case "playersinradius", "pir" -> playersInRadius(context.caster, number(first(args, "radius", "r"), 5.0d));
            case "livingentitiesinradius", "entitiesinradius", "eir" -> livingInRadius(context.caster, number(first(args, "radius", "r"), 5.0d));
            case "nearestplayer" -> nearestPlayer(context.caster, number(first(args, "radius", "r", "distance", "d"), 64.0d));
            default -> List.of(context.target);
        };
    }

    private static List<Entity> playersInRadius(Entity center, double radius) {
        MinecraftServer server = MythicMobsFabricMod.server();
        if (server == null) return List.of();
        double squared = radius * radius;
        List<Entity> result = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld() == center.getWorld() && player.squaredDistanceTo(center) <= squared) result.add(player);
        }
        return result;
    }

    private static List<Entity> livingInRadius(Entity center, double radius) {
        if (!(center.getWorld() instanceof ServerWorld world)) return List.of();
        Box box = center.getBoundingBox().expand(radius);
        return new ArrayList<>(world.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive));
    }

    private static List<Entity> nearestPlayer(Entity center, double radius) {
        Entity nearest = null;
        double best = radius * radius;
        for (Entity player : playersInRadius(center, radius)) {
            double distance = player.squaredDistanceTo(center);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest == null ? List.of() : List.of(nearest);
    }

    private static boolean message(CastContext context, Entity target, String text) {
        if (!(target instanceof ServerPlayerEntity player) || text == null) return false;
        player.sendMessage(Text.literal(resolveText(text, context, target)), false);
        return true;
    }

    private static boolean randomMessage(CastContext context, Entity target, String text) {
        if (text == null) return false;
        String[] options = text.split("\\|", -1);
        String selected = options[Math.floorMod(target.getRandom().nextInt(), options.length)];
        return message(context, target, selected);
    }

    private static boolean damage(Entity target, double amount) {
        if (!(target instanceof LivingEntity living) || amount <= 0.0d) return false;
        return living.damage(living.getDamageSources().generic(), (float) amount);
    }

    private static boolean damagePercent(Entity target, double percent) {
        if (!(target instanceof LivingEntity living) || percent <= 0.0d) return false;
        return damage(target, living.getMaxHealth() * percent / 100.0d);
    }

    private static boolean heal(Entity target, double amount) {
        if (!(target instanceof LivingEntity living) || amount <= 0.0d) return false;
        living.heal((float) amount);
        return true;
    }

    private static boolean healPercent(Entity target, double percent) {
        if (!(target instanceof LivingEntity living) || percent <= 0.0d) return false;
        living.heal((float) (living.getMaxHealth() * percent / 100.0d));
        return true;
    }

    private static boolean setHealth(Entity target, double amount) {
        if (!(target instanceof LivingEntity living)) return false;
        living.setHealth((float) Math.max(0.0d, Math.min(amount, living.getMaxHealth())));
        return true;
    }

    private static boolean potion(Entity target, String effect, int duration, int level) {
        if (!(target instanceof LivingEntity living) || effect == null) return false;
        Identifier id = identifier(effect, "minecraft:speed");
        return Registries.STATUS_EFFECT.getEntry(id).map(entry -> living.addStatusEffect(new StatusEffectInstance(entry,
                Math.max(1, duration), Math.max(0, level - 1)))).orElse(false);
    }

    private static boolean removePotion(Entity target, String effect) {
        if (!(target instanceof LivingEntity living) || effect == null) return false;
        return Registries.STATUS_EFFECT.getEntry(identifier(effect, "minecraft:speed"))
                .map(living::removeStatusEffect).orElse(false);
    }

    private static boolean clearPotions(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;
        return living.clearStatusEffects();
    }

    private static boolean ignite(Entity target, int ticks) {
        target.setOnFireForTicks(Math.max(0, ticks));
        return true;
    }

    private static boolean extinguish(Entity target) {
        target.extinguish();
        return true;
    }

    private static boolean velocity(Entity target, double x, double y, double z) {
        target.setVelocity(x, y, z);
        target.velocityModified = true;
        return true;
    }

    private static boolean throwTarget(Entity caster, Entity target, double horizontal, double vertical) {
        Vec3d delta = target.getPos().subtract(caster.getPos());
        Vec3d flat = new Vec3d(delta.x, 0.0d, delta.z);
        Vec3d direction = flat.lengthSquared() < 1.0E-8 ? caster.getRotationVec(1.0F) : flat.normalize();
        return velocity(target, direction.x * horizontal, vertical, direction.z * horizontal);
    }

    private static boolean pull(Entity caster, Entity target, double strength) {
        Vec3d delta = caster.getPos().subtract(target.getPos());
        if (delta.lengthSquared() < 1.0E-8) return true;
        Vec3d direction = delta.normalize().multiply(strength);
        return velocity(target, direction.x, direction.y, direction.z);
    }

    private static boolean leap(Entity target, double forward, double vertical) {
        Vec3d direction = target.getRotationVec(1.0F).normalize().multiply(forward);
        return velocity(target, direction.x, vertical, direction.z);
    }

    private static boolean teleport(Entity mover, Entity destination) {
        if (mover == null || destination == null || mover.getWorld() != destination.getWorld()) return false;
        mover.refreshPositionAndAngles(destination.getX(), destination.getY(), destination.getZ(), mover.getYaw(), mover.getPitch());
        return true;
    }

    private static boolean sound(Entity target, String sound, float volume, float pitch) {
        if (!(target.getWorld() instanceof ServerWorld world) || sound == null) return false;
        Identifier id = legacySound(sound);
        world.playSound(null, target.getX(), target.getY(), target.getZ(), Registries.SOUND_EVENT.get(id),
                SoundCategory.HOSTILE, Math.max(0.0f, volume), pitch);
        return true;
    }

    private static boolean particles(Entity target, String particle, int amount, double hSpread, double ySpread, double speed, double yOffset) {
        if (!(target.getWorld() instanceof ServerWorld world) || particle == null) return false;
        Object value = Registries.PARTICLE_TYPE.get(legacyParticle(particle));
        if (!(value instanceof ParticleEffect effect)) return false;
        world.spawnParticles(effect, target.getX(), target.getY() + yOffset, target.getZ(), Math.max(1, amount),
                hSpread, ySpread, hSpread, speed);
        return true;
    }

    private static boolean summon(CastContext context, Entity target, String mob, int amount, double noise, int level) {
        if (!(target.getWorld() instanceof ServerWorld world) || mob == null || mob.isBlank()) return false;
        boolean any = false;
        for (int i = 0; i < Math.max(1, amount); i++) {
            double x = target.getX() + (world.random.nextDouble() * 2.0d - 1.0d) * noise;
            double z = target.getZ() + (world.random.nextDouble() * 2.0d - 1.0d) * noise;
            any |= MythicMobsFabricMod.spawn(mob, world, x, target.getY(), z, Math.max(1, level)) != null;
        }
        return any;
    }

    private static boolean remove(Entity target) {
        return MythicMobsFabricMod.remove(target);
    }

    private static boolean kill(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;
        living.kill();
        return true;
    }

    private static boolean setName(CastContext context, Entity target, String name) {
        if (name == null) return false;
        target.setCustomName(Text.literal(resolveText(name, context, target)));
        target.setCustomNameVisible(true);
        return true;
    }

    private static boolean setGravity(Entity target, boolean useGravity) {
        target.setNoGravity(!useGravity);
        return true;
    }

    private static boolean feed(Entity target, int amount) {
        if (!(target instanceof ServerPlayerEntity player)) return false;
        player.getHungerManager().add(Math.max(0, amount), 0.0f);
        return true;
    }

    private static boolean command(CastContext context, String command, boolean console) {
        MinecraftServer server = MythicMobsFabricMod.server();
        if (server == null || command == null || command.isBlank()) return false;
        String resolved = resolveText(command, context, context.target);
        if (resolved.startsWith("/")) resolved = resolved.substring(1);
        if (!console && context.caster instanceof ServerPlayerEntity player) {
            server.getCommandManager().executeWithPrefix(player.getCommandSource(), resolved);
        } else {
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), resolved);
        }
        return true;
    }

    private static boolean nestedSkill(CastContext context, String id, Entity target) {
        if (id == null || id.isBlank()) return false;
        return cast(id, context.caster.getUuid(), target.getUuid(), context.parameters, context.depth + 1);
    }

    private static ParsedLine parse(String raw) {
        if (raw == null) return null;
        String input = raw.trim();
        if (input.isEmpty() || input.startsWith("#")) return null;

        List<String> inline = new ArrayList<>();
        int conditionIndex = findTopLevel(input, " ?");
        if (conditionIndex >= 0) {
            String tail = input.substring(conditionIndex + 2).trim();
            input = input.substring(0, conditionIndex).trim();
            for (String part : splitTopLevel(tail, '?')) if (!part.isBlank()) inline.add(part.trim());
        }

        String targeter = "";
        Map<String, String> targetArgs = Map.of();
        int targetIndex = findTopLevel(input, " @");
        if (targetIndex >= 0) {
            ParsedComponent parsedTargeter = parseComponent(input.substring(targetIndex + 1).trim());
            input = input.substring(0, targetIndex).trim();
            if (parsedTargeter != null) {
                targeter = parsedTargeter.name;
                targetArgs = parsedTargeter.args;
            }
        }

        ParsedComponent component = parseComponent(input);
        if (component == null) return null;
        String mechanic = normalizeMechanic(component.name);
        String free = component.freeArgument;
        return new ParsedLine(mechanic, component.args, free, targeter, targetArgs, List.copyOf(inline));
    }

    private static ParsedComponent parseComponent(String raw) {
        if (raw == null) return null;
        String input = raw.trim();
        if (input.isEmpty()) return null;
        int brace = input.indexOf('{');
        if (brace >= 0) {
            int end = matchingBrace(input, brace);
            String name = input.substring(0, brace).trim();
            String body = end > brace ? input.substring(brace + 1, end) : input.substring(brace + 1);
            return new ParsedComponent(normalize(name), parseArgs(body), "");
        }
        int space = input.indexOf(' ');
        if (space >= 0) return new ParsedComponent(normalize(input.substring(0, space)), Map.of(), input.substring(space + 1).trim());
        return new ParsedComponent(normalize(input), Map.of(), "");
    }

    private static Map<String, String> parseArgs(String body) {
        if (body == null || body.isBlank()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : splitTopLevel(body, ';')) {
            String entry = part.trim();
            if (entry.isEmpty()) continue;
            int eq = indexOfUnquoted(entry, '=');
            if (eq < 0) out.put(normalize(entry), "true");
            else out.put(normalize(entry.substring(0, eq)), unquote(entry.substring(eq + 1).trim()));
        }
        return Map.copyOf(out);
    }

    private static List<String> splitTopLevel(String input, char delimiter) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braces = 0;
        int brackets = 0;
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote && (i == 0 || input.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; current.append(c); continue; }
            if (c == '{') braces++;
            else if (c == '}') braces = Math.max(0, braces - 1);
            else if (c == '[') brackets++;
            else if (c == ']') brackets = Math.max(0, brackets - 1);
            if (c == delimiter && braces == 0 && brackets == 0) {
                out.add(current.toString());
                current.setLength(0);
            } else current.append(c);
        }
        out.add(current.toString());
        return out;
    }

    private static int findTopLevel(String input, String needle) {
        int braces = 0;
        char quote = 0;
        for (int i = 0; i <= input.length() - needle.length(); i++) {
            char c = input.charAt(i);
            if (quote != 0) {
                if (c == quote && (i == 0 || input.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; continue; }
            if (c == '{') braces++;
            else if (c == '}') braces = Math.max(0, braces - 1);
            if (braces == 0 && input.startsWith(needle, i)) return i;
        }
        return -1;
    }

    private static int matchingBrace(String input, int start) {
        int depth = 0;
        char quote = 0;
        for (int i = start; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quote != 0) {
                if (c == quote && input.charAt(Math.max(0, i - 1)) != '\\') quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; continue; }
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static int indexOfUnquoted(String input, char needle) {
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quote != 0) {
                if (c == quote && input.charAt(Math.max(0, i - 1)) != '\\') quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; continue; }
            if (c == needle) return i;
        }
        return -1;
    }

    private static Object get(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private static List<String> strings(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object element : list) out.add(String.valueOf(element));
            return List.copyOf(out);
        }
        return List.of(String.valueOf(value));
    }

    private static String first(Map<String, String> args, String... keys) {
        for (String key : keys) {
            String value = args.get(normalize(key));
            if (value != null) return value;
        }
        return null;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return fallback;
        try { return Double.parseDouble(String.valueOf(value).trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static boolean bool(String value, boolean fallback) {
        if (value == null) return fallback;
        return switch (normalize(value)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> fallback;
        };
    }

    private static double distance(Entity first, Entity second) {
        if (first == null || second == null || first.getWorld() != second.getWorld()) return Double.POSITIVE_INFINITY;
        return Math.sqrt(first.squaredDistanceTo(second));
    }

    private static boolean compare(double actual, String expression) {
        if (expression == null || expression.isBlank()) return true;
        String value = expression.trim();
        try {
            if (value.startsWith(">=")) return actual >= Double.parseDouble(value.substring(2));
            if (value.startsWith("<=")) return actual <= Double.parseDouble(value.substring(2));
            if (value.startsWith(">")) return actual > Double.parseDouble(value.substring(1));
            if (value.startsWith("<")) return actual < Double.parseDouble(value.substring(1));
            if (value.startsWith("=")) return actual == Double.parseDouble(value.substring(1));
            if (value.contains("to")) {
                String[] range = value.toLowerCase(Locale.ROOT).split("to", 2);
                double min = Double.parseDouble(range[0].trim());
                double max = Double.parseDouble(range[1].trim());
                return actual >= Math.min(min, max) && actual <= Math.max(min, max);
            }
            return actual == Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Entity entity(UUID id) {
        if (id == null) return null;
        MinecraftServer server = MythicMobsFabricMod.server();
        if (server == null) return null;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
        if (player != null) return player;
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static String resolveText(String raw, CastContext context, Entity target) {
        if (raw == null) return "";
        String text = unquote(raw);
        text = text.replace("<&co>", ":")
                .replace("<caster.name>", context.caster.getName().getString())
                .replace("<mob.name>", context.caster.getName().getString())
                .replace("<target.name>", target == null ? "" : target.getName().getString());
        for (Map.Entry<String, ?> entry : context.parameters.entrySet()) {
            text = text.replace("<" + entry.getKey() + ">", String.valueOf(entry.getValue()));
        }
        return text;
    }

    private static String unquote(String value) {
        if (value == null || value.length() < 2) return value;
        char first = value.charAt(0), last = value.charAt(value.length() - 1);
        return (first == last && (first == '\'' || first == '"')) ? value.substring(1, value.length() - 1) : value;
    }

    private static String normalizeMechanic(String raw) {
        String value = normalize(raw);
        if (value.startsWith("effect:")) value = value.substring("effect:".length());
        return value.replace("_", "").replace("-", "");
    }

    private static Identifier legacySound(String raw) {
        String value = normalize(raw);
        value = switch (value) {
            case "mob.endermen.portal", "mob.enderman.portal" -> "entity.enderman.teleport";
            case "random.explode" -> "entity.generic.explode";
            case "random.orb" -> "entity.experience_orb.pickup";
            default -> value;
        };
        return identifier(value, "minecraft:entity.experience_orb.pickup");
    }

    private static Identifier legacyParticle(String raw) {
        String value = normalize(raw).replace("happyvillager", "happy_villager")
                .replace("reddust", "dust")
                .replace("largesmoke", "large_smoke")
                .replace("witchmagic", "witch");
        return identifier(value, "minecraft:crit");
    }

    private static Identifier identifier(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (value.isEmpty()) return Identifier.of(fallback);
        if (!value.contains(":")) value = "minecraft:" + value;
        try { return Identifier.of(value); }
        catch (RuntimeException ignored) { return Identifier.of(fallback); }
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private record CastContext(String skillId, Entity caster, Entity target, Map<String, ?> parameters, int depth) {}
    private record ParsedLine(String mechanic, Map<String, String> args, String freeArgument,
                              String targeter, Map<String, String> targetArgs, List<String> inlineConditions) {}
    private record ParsedComponent(String name, Map<String, String> args, String freeArgument) {}
}
