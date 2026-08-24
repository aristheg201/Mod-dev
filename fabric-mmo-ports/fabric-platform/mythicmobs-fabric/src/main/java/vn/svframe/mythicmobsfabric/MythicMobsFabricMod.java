package vn.svframe.mythicmobsfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.server.command.CommandManager.literal;

public final class MythicMobsFabricMod implements ModInitializer {
    public static final String ID = "mythicmobsfabric";

    @FunctionalInterface
    public interface ExternalSkill {
        boolean cast(UUID caster, UUID target, Map<String, ?> parameters);
    }

    private static final Map<String, ExternalSkill> EXTERNAL_SKILLS = new ConcurrentHashMap<>();
    private static volatile MinecraftServer server;
    private static long ticks;

    @Override
    public void onInitialize() {
        registerBuiltins();
        ServerLifecycleEvents.SERVER_STARTED.register(value -> {
            server = value;
            System.out.println("[MythicMobs-Fabric] runtime online; externalSkills=" + EXTERNAL_SKILLS.size());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(value -> server = null);
        ServerTickEvents.END_SERVER_TICK.register(value -> ticks++);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("mythicmobsfabric")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("status").executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                    "MythicMobs Fabric ticks=" + ticks + " | externalSkills=" + EXTERNAL_SKILLS.size()), false);
                            return 1;
                        }))));
    }

    public static MinecraftServer server() {
        return server;
    }

    public static AutoCloseable registerExternalSkill(String id, ExternalSkill skill) {
        String key = normalize(id);
        if (key.isEmpty() || skill == null) throw new IllegalArgumentException("Skill id and handler are required");
        EXTERNAL_SKILLS.put(key, skill);
        return () -> EXTERNAL_SKILLS.remove(key, skill);
    }

    public static boolean castExternal(String id, UUID caster, UUID target, Map<String, ?> parameters) {
        ExternalSkill skill = EXTERNAL_SKILLS.get(normalize(id));
        if (skill == null) return false;
        return skill.cast(caster, target == null ? caster : target, parameters == null ? Map.of() : parameters);
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
            int ticks = (int) Math.max(0, number(parameters, "ticks", number(parameters, "duration", 100.0d)));
            entity.setOnFireForTicks(ticks);
            return true;
        });
        EXTERNAL_SKILLS.putIfAbsent("velocity", (caster, target, parameters) -> {
            Entity entity = entity(target);
            if (entity == null) return false;
            Vec3d current = entity.getVelocity();
            entity.setVelocity(
                    number(parameters, "x", current.x),
                    number(parameters, "y", current.y),
                    number(parameters, "z", current.z));
            entity.velocityModified = true;
            return true;
        });
    }

    private static Entity entity(UUID uuid) {
        MinecraftServer current = server;
        if (current == null || uuid == null) return null;
        ServerPlayerEntity player = current.getPlayerManager().getPlayer(uuid);
        if (player != null) return player;
        for (ServerWorld world : current.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static double number(Map<String, ?> parameters, String key, double fallback) {
        Object value = parameters.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
