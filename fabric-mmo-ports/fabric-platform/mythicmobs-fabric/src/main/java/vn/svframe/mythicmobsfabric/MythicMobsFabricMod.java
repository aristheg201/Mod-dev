package vn.svframe.mythicmobsfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import vn.svframe.compat.YamlLite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.minecraft.server.command.CommandManager.literal;

public final class MythicMobsFabricMod implements ModInitializer {
    public static final String ID = "mythicmobsfabric";
    private static final Logger LOG = Logger.getLogger("MythicMobs-Fabric");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MythicMobs");

    @FunctionalInterface
    public interface ExternalSkill {
        boolean cast(UUID caster, UUID target, Map<String, ?> parameters);
    }

    private record MobDefinition(String id, String entityType, String display, double health, double damage) {}
    private record Scheduled(long tick, Runnable task) {}

    private static final Map<String, ExternalSkill> EXTERNAL_SKILLS = new ConcurrentHashMap<>();
    private static final Map<String, MobDefinition> MOBS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> SKILLS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> ACTIVE_MOBS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Scheduled> SCHEDULED = new ConcurrentLinkedQueue<>();
    private static volatile MinecraftServer server;
    private static long ticks;

    @Override
    public void onInitialize() {
        registerBuiltins();
        reloadDefinitions();
        ServerLifecycleEvents.SERVER_STARTED.register(value -> {
            server = value;
            reloadDefinitions();
            LOG.info("MythicMobs Fabric runtime online; " + definitionSummary());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(value -> {
            server = null;
            ACTIVE_MOBS.clear();
            SCHEDULED.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(value -> {
            ticks++;
            runScheduled();
            if ((ticks & 31L) == 0L) ACTIVE_MOBS.keySet().removeIf(id -> entity(id) == null);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("mythicmobsfabric")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("status").executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("MythicMobs Fabric | " + definitionSummary()), false);
                            return 1;
                        }))
                        .then(literal("reload").executes(ctx -> {
                            if (!reloadDefinitions()) {
                                ctx.getSource().sendError(Text.literal("MythicMobs reload failed. Check server log."));
                                return 0;
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal("MythicMobs reloaded | " + definitionSummary()), true);
                            return 1;
                        }))));
    }

    public static MinecraftServer server() { return server; }
    public static boolean hasMob(String id) { return MOBS.containsKey(normalize(id)); }
    public static boolean hasSkill(String id) {
        String key = normalize(id);
        return SKILLS.containsKey(key) || EXTERNAL_SKILLS.containsKey(key);
    }
    public static int activeCount() {
        ACTIVE_MOBS.keySet().removeIf(id -> entity(id) == null);
        return ACTIVE_MOBS.size();
    }
    public static String definitionSummary() {
        return "mobs=" + MOBS.size() + ",skills=" + SKILLS.size() + ",active=" + activeCount() + ",externalSkills=" + EXTERNAL_SKILLS.size();
    }

    static Map<String, Object> skillDefinition(String id) {
        return SKILLS.get(normalize(id));
    }

    static void schedule(int delayTicks, Runnable task) {
        if (task == null) return;
        if (delayTicks <= 0) {
            MinecraftServer current = server;
            if (current != null) current.execute(task); else task.run();
            return;
        }
        SCHEDULED.add(new Scheduled(ticks + delayTicks, task));
    }

    public static Entity spawn(String id, ServerWorld world, double x, double y, double z, int level) {
        if (world == null) return null;
        MobDefinition definition = MOBS.get(normalize(id));
        if (definition == null) return null;
        Identifier typeId = identifier(definition.entityType());
        EntityType<?> type = Registries.ENTITY_TYPE.get(typeId);
        Entity entity = type.create(world);
        if (entity == null) {
            LOG.warning("Could not create MythicMob " + definition.id() + " using entity type " + typeId);
            return null;
        }
        entity.refreshPositionAndAngles(x, y, z, world.random.nextFloat() * 360.0F, 0.0F);
        if (entity instanceof LivingEntity living) {
            if (definition.health() > 0.0d) living.setHealth((float) Math.min(definition.health(), living.getMaxHealth()));
            if (definition.display() != null && !definition.display().isBlank()) {
                living.setCustomName(Text.literal(stripFormatting(definition.display())));
                living.setCustomNameVisible(true);
            }
        }
        if (!world.spawnEntity(entity)) return null;
        ACTIVE_MOBS.put(entity.getUuid(), definition.id());
        return entity;
    }

    public static boolean remove(Entity entity) {
        if (entity == null) return false;
        ACTIVE_MOBS.remove(entity.getUuid());
        if (!entity.isRemoved()) entity.discard();
        return true;
    }

    public static AutoCloseable registerExternalSkill(String id, ExternalSkill skill) {
        String key = normalize(id);
        if (key.isEmpty() || skill == null) throw new IllegalArgumentException("Skill id and handler are required");
        EXTERNAL_SKILLS.put(key, skill);
        return () -> EXTERNAL_SKILLS.remove(key, skill);
    }

    public static boolean castExternal(String id, UUID caster, UUID target, Map<String, ?> parameters) {
        String key = normalize(id);
        ExternalSkill skill = EXTERNAL_SKILLS.get(key);
        if (skill != null) return skill.cast(caster, target == null ? caster : target, parameters == null ? Map.of() : parameters);
        if (!SKILLS.containsKey(key)) return false;
        return MythicSkillRuntime.cast(key, caster, target == null ? caster : target, parameters == null ? Map.of() : parameters);
    }

    private static boolean reloadDefinitions() {
        try {
            Map<String, MobDefinition> nextMobs = new LinkedHashMap<>();
            Map<String, Map<String, Object>> nextSkills = new LinkedHashMap<>();
            loadMobs(ROOT.resolve("Mobs"), nextMobs);
            loadMobs(ROOT.resolve("mobs"), nextMobs);
            loadSkills(ROOT.resolve("Skills"), nextSkills);
            loadSkills(ROOT.resolve("skills"), nextSkills);
            MOBS.clear(); MOBS.putAll(nextMobs);
            SKILLS.clear(); SKILLS.putAll(nextSkills);
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load MythicMobs legacy configuration", e);
            return false;
        }
    }

    private static void loadMobs(Path directory, Map<String, MobDefinition> target) throws IOException {
        for (Path file : yamlFiles(directory)) {
            Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
                Map<String, Object> section = stringMap(raw);
                String type = text(section, "Type", text(section, "type", "ZOMBIE"));
                String display = text(section, "Display", text(section, "display", entry.getKey()));
                double health = number(section, "Health", number(section, "health", 0.0d));
                double damage = number(section, "Damage", number(section, "damage", 0.0d));
                target.put(normalize(entry.getKey()), new MobDefinition(entry.getKey(), type, display, health, damage));
            }
        }
    }

    private static void loadSkills(Path directory, Map<String, Map<String, Object>> target) throws IOException {
        for (Path file : yamlFiles(directory)) {
            Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
            for (Map.Entry<String, Object> entry : root.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> raw) target.put(normalize(entry.getKey()), Map.copyOf(stringMap(raw)));
            }
        }
    }

    private static List<Path> yamlFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".yml") || name.endsWith(".yaml");
            }).sorted().toList();
        }
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) out.put(String.valueOf(entry.getKey()), entry.getValue());
        return out;
    }
    private static String text(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static void registerBuiltins() {
        EXTERNAL_SKILLS.putIfAbsent("damage", (caster, target, parameters) -> {
            Entity entity = entity(target);
            if (!(entity instanceof LivingEntity living)) return false;
            double amount = number(parameters, "amount", number(parameters, "damage", 1.0d));
            if (!(amount > 0.0d)) return false;
            living.damage(living.getDamageSources().generic(), (float) amount);
            return true;
        });
        EXTERNAL_SKILLS.putIfAbsent("heal", (caster, target, parameters) -> {
            Entity entity = entity(target);
            if (!(entity instanceof LivingEntity living)) return false;
            double amount = number(parameters, "amount", number(parameters, "heal", 1.0d));
            if (!(amount > 0.0d)) return false;
            living.heal((float) amount);
            return true;
        });
        EXTERNAL_SKILLS.putIfAbsent("message", (caster, target, parameters) -> {
            Entity entity = entity(target);
            if (!(entity instanceof ServerPlayerEntity player)) return false;
            Object value = parameters.get("message");
            if (value == null) value = parameters.get("text");
            if (value == null) return false;
            player.sendMessage(Text.literal(String.valueOf(value)), false);
            return true;
        });
        EXTERNAL_SKILLS.putIfAbsent("ignite", (caster, target, parameters) -> {
            Entity entity = entity(target);
            if (entity == null) return false;
            int fireTicks = (int) Math.max(0, number(parameters, "ticks", number(parameters, "duration", 100.0d)));
            entity.setOnFireForTicks(fireTicks);
            return true;
        });
        EXTERNAL_SKILLS.putIfAbsent("velocity", (caster, target, parameters) -> {
            Entity entity = entity(target);
            if (entity == null) return false;
            Vec3d current = entity.getVelocity();
            entity.setVelocity(number(parameters, "x", current.x), number(parameters, "y", current.y), number(parameters, "z", current.z));
            entity.velocityModified = true;
            return true;
        });
    }

    private static void runScheduled() {
        int size = SCHEDULED.size();
        for (int i = 0; i < size; i++) {
            Scheduled scheduled = SCHEDULED.poll();
            if (scheduled == null) break;
            if (scheduled.tick <= ticks) {
                try { scheduled.task.run(); }
                catch (Throwable throwable) { LOG.log(Level.SEVERE, "Scheduled MythicMobs task failed", throwable); }
            } else SCHEDULED.add(scheduled);
        }
    }

    private static Entity entity(UUID uuid) {
        MinecraftServer current = server;
        if (current == null || uuid == null) return null;
        ServerPlayerEntity player = current.getPlayerManager().getPlayer(uuid);
        if (player != null) return player;
        for (ServerWorld world : current.getWorlds()) {
            Entity found = world.getEntity(uuid);
            if (found != null) return found;
        }
        return null;
    }

    private static double number(Map<String, ?> parameters, String key, double fallback) {
        Object value = parameters.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value != null) {
            try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }
    private static Identifier identifier(String raw) {
        String value = raw == null ? "zombie" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!value.contains(":")) value = "minecraft:" + value;
        try { return Identifier.of(value); } catch (RuntimeException ignored) { return Identifier.of("minecraft:zombie"); }
    }
    private static String stripFormatting(String value) {
        return value.replaceAll("<[^>]+>", "").replaceAll("&[0-9A-FK-ORa-fk-or]", "");
    }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
