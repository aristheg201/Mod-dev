package vn.svframe.mmocorefabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import vn.svframe.compat.YamlLite;
import vn.svframe.mmocorefabric.runtime.gameplay.ProfessionRuntime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Native Fabric ingress for MMOCore profession experience sources.
 *
 * Progression stays in the existing ProfessionRuntime. This class only adapts
 * Minecraft/Fabric events to the same legacy source definitions and amount/
 * curve semantics used by the original plugin.
 */
public final class MMOCoreProfessionExperienceMod implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("MMOCore-Fabric/Professions");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOCore");
    private static final long LAST_ATTACKER_TTL = 1200L;
    private static final long COMBAT_TTL = 200L;

    private static final Map<String, List<SourceSpec>> SOURCES = new ConcurrentHashMap<>();
    private static final Map<String, Curve> CURVES = new ConcurrentHashMap<>();
    private static final Map<UUID, Attacker> LAST_ATTACKER = new ConcurrentHashMap<>();
    private static final Map<UUID, MovementSample> MOVEMENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COMBAT_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, ProjectileOrigin> PROJECTILE_ORIGINS = new ConcurrentHashMap<>();
    private static final Set<PlacedBlock> PLAYER_PLACED = ConcurrentHashMap.newKeySet();
    private static final Deque<PendingDamage> PENDING_DAMAGE = new ArrayDeque<>();
    private static volatile long tick;
    private static volatile long lastConfigStamp = Long.MIN_VALUE;

    @Override
    public void onInitialize() {
        reloadSources(ROOT);

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
            if (serverPlayer.interactionManager.getGameMode() != GameMode.SURVIVAL) return;
            PlacedBlock key = new PlacedBlock(world.getRegistryKey().getValue().toString(), pos.asLong());
            boolean playerPlaced = PLAYER_PLACED.remove(key);
            dispatchMineBlock(serverPlayer, state, playerPlaced);
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamageTaken, damageTaken, blocked) -> {
            Entity attacker = source.getAttacker();
            if (attacker instanceof ServerPlayerEntity player && victim != player) {
                LAST_ATTACKER.put(victim.getUuid(), new Attacker(player.getUuid(), tick));
                COMBAT_UNTIL.put(player.getUuid(), tick + COMBAT_TTL);
                dispatchDamageDealt(player, victim, source, damageTaken);
            }
            if (victim instanceof ServerPlayerEntity player && !player.isDead()) {
                COMBAT_UNTIL.put(player.getUuid(), tick + COMBAT_TTL);
                synchronized (PENDING_DAMAGE) {
                    PENDING_DAMAGE.addLast(new PendingDamage(player.getUuid(), damageCause(source), Math.min(damageTaken, player.getMaxHealth()), tick + 2L));
                }
            }
            if (source.getSource() instanceof ProjectileEntity projectile && victim instanceof LivingEntity living) {
                awardProjectileHit(projectile, living, damageTaken);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (victim instanceof ServerPlayerEntity) return;
            Attacker record = LAST_ATTACKER.remove(victim.getUuid());
            UUID playerId = record == null ? null : record.player();
            if (playerId == null && source.getAttacker() instanceof ServerPlayerEntity direct) playerId = direct.getUuid();
            if (playerId == null) return;
            MinecraftServer server = victim.getServer();
            ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(playerId);
            if (player != null) dispatchKillMob(player, victim);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            LAST_ATTACKER.entrySet().removeIf(entry -> tick - entry.getValue().tick() > LAST_ATTACKER_TTL);
            COMBAT_UNTIL.entrySet().removeIf(entry -> entry.getValue() < tick);
            PROJECTILE_ORIGINS.entrySet().removeIf(entry -> {
                Entity entity = findEntity(server, entry.getKey());
                return entity == null || entity.isRemoved();
            });
            drainPendingDamage(server);
            processMovement(server);
            if (tick % 20L == 0L) processPlay(server);
            if (tick % 100L == 0L) reloadIfChanged(ROOT);
        });
    }

    public static void markPlaced(ServerPlayerEntity player, String world, BlockPos pos, BlockState state) {
        if (player == null || world == null || pos == null || state == null) return;
        PlacedBlock key = new PlacedBlock(world, pos.asLong());
        PLAYER_PLACED.add(key);
        dispatchPlaceBlock(player, state);
    }

    /** Records the original launch location used by the legacy projectile EXP source. */
    public static void recordProjectileLaunch(ProjectileEntity projectile, ServerPlayerEntity owner) {
        if (projectile == null || owner == null || projectile.getWorld().isClient) return;
        PROJECTILE_ORIGINS.put(projectile.getUuid(), new ProjectileOrigin(owner.getUuid(), projectile.getX(), projectile.getY(), projectile.getZ()));
    }

    /** Legacy projectile source multiplier is final damage multiplied by travel distance. */
    public static void awardProjectileHit(ProjectileEntity projectile, LivingEntity target, double finalDamage) {
        if (projectile == null || target == null || !(finalDamage > 0.0)) return;
        ProjectileOrigin origin = PROJECTILE_ORIGINS.get(projectile.getUuid());
        UUID ownerId = origin == null ? null : origin.owner();
        if (ownerId == null && projectile.getOwner() instanceof ServerPlayerEntity owner) ownerId = owner.getUuid();
        if (ownerId == null) return;
        MinecraftServer server = target.getServer();
        ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(ownerId);
        if (player == null) return;
        double ox = origin == null ? projectile.getX() : origin.x();
        double oy = origin == null ? projectile.getY() : origin.y();
        double oz = origin == null ? projectile.getZ() : origin.z();
        double dx = target.getX() - ox, dy = target.getY() - oy, dz = target.getZ() - oz;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        String projectileType = projectile.getType().toString().toLowerCase(Locale.ROOT).contains("trident") ? "trident" : "arrow";
        dispatch(player, "projectile", spec -> {
            String configured = spec.param("type");
            if (configured != null && !norm(configured).equals(projectileType)) return 0.0;
            return finalDamage * distance;
        });
    }

    public static void awardConsumed(ServerPlayerEntity player, ItemStack stack) {
        awardItem(player, "eat", stack, 1.0);
    }

    public static void awardFish(ServerPlayerEntity player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        awardItem(player, "fishitem", stack, Math.max(1, stack.getCount()));
    }

    public static void awardSmelt(ServerPlayerEntity player, ItemStack stack) {
        awardItem(player, "smeltitem", stack, 1.0);
    }

    public static void awardCraft(ServerPlayerEntity player, ItemStack stack, int craftedAmount) {
        awardItem(player, "craftitem", stack, Math.max(1, craftedAmount));
    }

    public static void awardTame(ServerPlayerEntity player, LivingEntity entity) {
        if (player == null || entity == null) return;
        String type = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
        if (!normId(type).equals("minecraft:wolf")) return;
        dispatch(player, "tame", spec -> 1.0);
    }

    public static void awardResource(ServerPlayerEntity player, String resource, double difference) {
        if (player == null || !(difference > 0.0)) return;
        dispatch(player, "resource", spec -> {
            String configured = spec.param("type");
            return configured == null || norm(configured).equals(norm(resource)) ? difference : 0.0;
        });
    }

    public static void reloadSources(Path root) {
        try {
            Map<String, List<String>> reusable = loadReusable(root.resolve("exp-sources.yml"));
            Map<String, List<SourceSpec>> nextSources = new LinkedHashMap<>();
            Map<String, Curve> nextCurves = new LinkedHashMap<>();
            Path professions = root.resolve("professions");
            if (Files.isDirectory(professions)) {
                try (var files = Files.walk(professions)) {
                    for (Path file : files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                            .sorted(Comparator.comparing(Path::toString)).toList()) {
                        Map<String, Object> yaml = YamlLite.map(YamlLite.parse(file));
                        String profession = id(file);
                        List<SourceSpec> specs = new ArrayList<>();
                        for (String line : sourceLines(yaml.get("exp-sources"))) expand(line, reusable, new LinkedHashSet<>(), specs);
                        nextSources.put(profession, List.copyOf(specs));
                        nextCurves.put(profession, Curve.parse(root, string(yaml.get("exp-curve"), "levels")));
                    }
                }
            }
            SOURCES.clear();
            SOURCES.putAll(nextSources);
            CURVES.clear();
            CURVES.putAll(nextCurves);
            lastConfigStamp = configStamp(root);
            LOG.info("Loaded profession EXP ingress: professions=" + SOURCES.size() + ", sources=" + SOURCES.values().stream().mapToInt(List::size).sum());
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Failed to reload profession EXP sources; keeping previous snapshot", exception);
        }
    }

    private static void reloadIfChanged(Path root) {
        try {
            long stamp = configStamp(root);
            if (stamp != lastConfigStamp) reloadSources(root);
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Could not inspect profession config timestamp", exception);
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

    private static void processMovement(MinecraftServer server) {
        Set<UUID> online = new LinkedHashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            online.add(id);
            MovementSample now = MovementSample.of(player);
            MovementSample before = MOVEMENT.put(id, now);
            if (before == null || !before.world().equals(now.world())) continue;
            int dx = now.blockX() - before.blockX();
            int dy = now.blockY() - before.blockY();
            int dz = now.blockZ() - before.blockZ();
            if (dx == 0 && dy == 0 && dz == 0) continue;

            double blockDistance = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
            dispatch(player, "move", spec -> movingTypeMatches(player, spec.param("type")) ? blockDistance : 0.0);

            if (dy > 0 && player.getWorld() instanceof ServerWorld world) {
                BlockState from = world.getBlockState(new BlockPos(before.blockX(), before.blockY(), before.blockZ()));
                String material = Registries.BLOCK.getId(from.getBlock()).toString();
                dispatch(player, "climb", spec -> climbTypeMatches(spec, material) ? dy : 0.0);
            }

            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                String type = Registries.ENTITY_TYPE.getId(vehicle.getType()).toString();
                double x = now.x() - before.x(), y = now.y() - before.y(), z = now.z() - before.z();
                double preciseDistance = Math.sqrt(x * x + y * y + z * z);
                dispatch(player, "ride", spec -> matchesType(spec, type) ? preciseDistance : 0.0);
            }
        }
        MOVEMENT.keySet().removeIf(id -> !online.contains(id));
    }

    private static void processPlay(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            String world = player.getServerWorld().getRegistryKey().getValue().toString();
            dispatch(player, "play", spec -> {
                if (spec.bool("in-combat", false) && COMBAT_UNTIL.getOrDefault(player.getUuid(), 0L) < tick) return 0.0;
                String configuredWorld = spec.param("world");
                if (configuredWorld != null && !worldMatches(configuredWorld, world)) return 0.0;
                double x1 = spec.number("x1", Double.NEGATIVE_INFINITY), x2 = spec.number("x2", Double.POSITIVE_INFINITY);
                double z1 = spec.number("z1", Double.NEGATIVE_INFINITY), z2 = spec.number("z2", Double.POSITIVE_INFINITY);
                double minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
                return player.getX() > minX && player.getX() < maxX && player.getZ() > minZ && player.getZ() < maxZ ? 1.0 : 0.0;
            });
        }
    }

    private static void awardItem(ServerPlayerEntity player, String mechanic, ItemStack stack, double multiplier) {
        if (player == null || stack == null || stack.isEmpty() || !(multiplier > 0.0)) return;
        String material = Registries.ITEM.getId(stack.getItem()).toString();
        dispatch(player, mechanic, spec -> matchesType(spec, material) ? multiplier : 0.0);
    }

    private static void dispatchMineBlock(ServerPlayerEntity player, BlockState state, boolean playerPlaced) {
        String material = Registries.BLOCK.getId(state.getBlock()).toString();
        boolean mature = mature(state);
        boolean silkTouch = hasSilkTouch(player);
        dispatch(player, "mineblock", spec -> {
            if (!matchesType(spec, material)) return 0.0;
            if (spec.bool("crop", false) && !mature) return 0.0;
            if (!spec.bool("player-placed", false) && playerPlaced) return 0.0;
            if (spec.bool("silk-touch", true) && silkTouch) return 0.0;
            return 1.0;
        });
    }

    private static void dispatchPlaceBlock(ServerPlayerEntity player, BlockState state) {
        String material = Registries.BLOCK.getId(state.getBlock()).toString();
        dispatch(player, "placeblock", spec -> matchesType(spec, material) ? 1.0 : 0.0);
    }

    private static void dispatchKillMob(ServerPlayerEntity player, LivingEntity victim) {
        String type = Registries.ENTITY_TYPE.getId(victim.getType()).toString();
        String name = victim.hasCustomName() ? victim.getCustomName().getString() : null;
        if (victim.getCommandTags().contains("spawner_spawned") || victim.getCommandTags().contains("mmocore:spawner_spawned")) return;
        dispatch(player, "killmob", spec -> {
            if (!matchesType(spec, type)) return 0.0;
            String expectedName = spec.param("name");
            if (expectedName != null && !Objects.equals(expectedName, name)) return 0.0;
            return 1.0;
        });
    }

    private static void dispatchDamageDealt(ServerPlayerEntity player, LivingEntity victim, DamageSource source, float damage) {
        double effective = Math.min(Math.max(0.0, damage), victim.getMaxHealth());
        if (effective <= 0.0) return;
        String damageType = outgoingDamageType(player, source);
        dispatch(player, "damagedealt", spec -> {
            String configured = spec.param("type");
            if (configured != null && !norm(configured).equals(damageType)) return 0.0;
            return effective;
        });
    }

    private static void dispatchDamageTaken(ServerPlayerEntity player, String cause, double effective) {
        if (player.isDead() || effective <= 0.0) return;
        dispatch(player, "damagetaken", spec -> {
            String configured = spec.param("type");
            if (configured != null && !norm(configured).equals(norm(cause))) return 0.0;
            return effective;
        });
    }

    private static void drainPendingDamage(MinecraftServer server) {
        while (true) {
            PendingDamage pending;
            synchronized (PENDING_DAMAGE) {
                pending = PENDING_DAMAGE.peekFirst();
                if (pending == null || pending.dueTick() > tick) return;
                PENDING_DAMAGE.removeFirst();
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(pending.player());
            if (player != null) dispatchDamageTaken(player, pending.cause(), pending.damage());
        }
    }

    private static void dispatch(ServerPlayerEntity player, String mechanic, Multiplier multiplier) {
        ProfessionRuntime runtime;
        try {
            runtime = ProfessionRuntime.active();
        } catch (IllegalStateException ignored) {
            return;
        }
        for (Map.Entry<String, List<SourceSpec>> entry : SOURCES.entrySet()) {
            String profession = entry.getKey();
            Curve curve = CURVES.getOrDefault(profession, level -> 100.0 * level);
            for (SourceSpec spec : entry.getValue()) {
                if (!spec.mechanic().equals(mechanic)) continue;
                double factor = multiplier.value(spec);
                if (!(factor > 0.0) || !Double.isFinite(factor)) continue;
                double amount = spec.amount().roll() * factor;
                if (!(amount > 0.0) || !Double.isFinite(amount)) continue;
                runtime.grant(player.getUuid(), profession, mechanic, amount, curve::required);
            }
        }
    }

    private static Map<String, List<String>> loadReusable(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return Map.of();
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : root.entrySet()) out.put(norm(entry.getKey()), sourceLines(entry.getValue()));
        return Map.copyOf(out);
    }

    private static void expand(String line, Map<String, List<String>> reusable, Set<String> stack, List<SourceSpec> out) {
        SourceSpec spec = SourceSpec.parse(line);
        if (!spec.mechanic().equals("from")) {
            out.add(spec);
            return;
        }
        String source = norm(spec.param("source"));
        if (source.isEmpty() || !stack.add(source)) return;
        for (String nested : reusable.getOrDefault(source, List.of())) expand(nested, reusable, stack, out);
        stack.remove(source);
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

    private static boolean movingTypeMatches(ServerPlayerEntity player, String configured) {
        if (player.getVehicle() != null) return false;
        if (configured == null || configured.isBlank()) return true;
        return switch (norm(configured)) {
            case "sneak" -> player.isSneaking();
            case "fly" -> player.getAbilities().flying || player.isInPose(EntityPose.GLIDING);
            case "swim" -> !player.getBlockStateAtPos().getFluidState().isEmpty();
            case "sprint" -> player.isSprinting();
            case "walk" -> !player.isSneaking() && !player.isSprinting() && player.isOnGround()
                    && !player.getAbilities().flying && !player.isInPose(EntityPose.GLIDING)
                    && player.getBlockStateAtPos().getFluidState().isEmpty();
            default -> false;
        };
    }

    private static boolean climbTypeMatches(SourceSpec spec, String material) {
        String actual = normId(material);
        String configured = spec.param("type");
        if (configured == null || configured.isBlank()) {
            return actual.endsWith(":ladder") || actual.endsWith(":vine") || actual.endsWith(":weeping_vines")
                    || actual.endsWith(":weeping_vines_plant") || actual.endsWith(":twisting_vines") || actual.endsWith(":twisting_vines_plant");
        }
        String expected = normId(configured);
        if (expected.endsWith(":weeping_vines")) return actual.endsWith(":weeping_vines") || actual.endsWith(":weeping_vines_plant");
        if (expected.endsWith(":twisting_vines")) return actual.endsWith(":twisting_vines") || actual.endsWith(":twisting_vines_plant");
        return actual.equals(expected);
    }

    private static boolean matchesType(SourceSpec spec, String registryId) {
        String configured = spec.param("type");
        if (configured == null || configured.isBlank()) return true;
        String expected = normId(configured), actual = normId(registryId);
        return actual.equals(expected) || actual.endsWith(":" + expected) || expected.endsWith(":" + actual);
    }

    private static boolean worldMatches(String configured, String actual) {
        String expected = configured.trim().toLowerCase(Locale.ROOT), value = actual.trim().toLowerCase(Locale.ROOT);
        return value.equals(expected) || value.endsWith(":" + expected);
    }

    private static String outgoingDamageType(ServerPlayerEntity player, DamageSource source) {
        Entity direct = source.getSource();
        if (direct instanceof ProjectileEntity) return "projectile";
        ItemStack weapon = player.getMainHandStack();
        return weapon == null || weapon.isEmpty() ? "unarmed" : "physical";
    }

    private static String damageCause(DamageSource source) {
        String name = source.getName();
        if (name == null || name.isBlank()) return "custom";
        return norm(name).replace('-', '_');
    }

    private static boolean hasSilkTouch(ServerPlayerEntity player) {
        try {
            var registry = player.getServerWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            var silk = registry.getEntry(net.minecraft.enchantment.Enchantments.SILK_TOUCH).orElse(null);
            return silk != null && net.minecraft.enchantment.EnchantmentHelper.getLevel(silk, player.getMainHandStack()) > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean mature(BlockState state) {
        for (var property : state.getProperties()) {
            if (!property.getName().equalsIgnoreCase("age")) continue;
            Comparable current = state.get((net.minecraft.state.property.Property) property);
            int currentAge = integer(current, Integer.MIN_VALUE), maxAge = Integer.MIN_VALUE;
            for (Object allowed : property.getValues()) maxAge = Math.max(maxAge, integer(allowed, Integer.MIN_VALUE));
            return currentAge != Integer.MIN_VALUE && currentAge >= maxAge;
        }
        return true;
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
        if (player != null) return player;
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private static String id(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return norm(dot > 0 ? name.substring(0, dot) : name);
    }

    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value).trim(); }
    private static String norm(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
    private static String normId(String value) {
        String out = norm(value);
        if (out.startsWith("minecraft:")) return out;
        return out.contains(":") ? out : "minecraft:" + out;
    }

    private record PlacedBlock(String world, long pos) {}
    private record Attacker(UUID player, long tick) {}
    private record PendingDamage(UUID player, String cause, double damage, long dueTick) {}
    private record ProjectileOrigin(UUID owner, double x, double y, double z) {}
    private record MovementSample(String world, double x, double y, double z, int blockX, int blockY, int blockZ) {
        static MovementSample of(ServerPlayerEntity player) {
            BlockPos pos = player.getBlockPos();
            return new MovementSample(player.getServerWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    @FunctionalInterface private interface Multiplier { double value(SourceSpec spec); }

    @FunctionalInterface
    private interface Curve {
        double required(int level);

        static Curve parse(Path root, String raw) throws IOException {
            String value = raw == null ? "levels" : raw.trim();
            Path path = resolveCurve(root, value);
            if (path != null && Files.isRegularFile(path)) {
                List<Double> levels = new ArrayList<>();
                for (String line : Files.readAllLines(path)) {
                    String clean = line.trim();
                    if (clean.isEmpty() || clean.startsWith("#")) continue;
                    levels.add(Double.parseDouble(clean));
                }
                if (!levels.isEmpty()) return level -> levels.get(Math.min(Math.max(level, 1), levels.size()) - 1);
            }
            NumericExpression expression = NumericExpression.compile(value.replace("{level}", "level"));
            return level -> expression.eval(level);
        }

        private static Path resolveCurve(Path root, String value) {
            String clean = value.replace('\\', '/');
            if (clean.matches("[A-Za-z0-9_.\\-/]+")) {
                Path direct = root.resolve(clean).normalize();
                if (Files.isRegularFile(direct)) return direct;
                if (!clean.toLowerCase(Locale.ROOT).endsWith(".txt")) {
                    Path expCurves = root.resolve("exp-curves").resolve(clean + ".txt").normalize();
                    if (Files.isRegularFile(expCurves)) return expCurves;
                    Path legacy = root.resolve("expcurves").resolve(clean + ".txt").normalize();
                    if (Files.isRegularFile(legacy)) return legacy;
                }
            }
            return null;
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
        boolean bool(String key, boolean fallback) {
            String value = param(key);
            if (value == null) return fallback;
            return switch (norm(value)) {
                case "true", "yes", "on", "1" -> true;
                case "false", "no", "off", "0" -> false;
                default -> fallback;
            };
        }
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
                    double a = Double.parseDouble(value.substring(0, split).trim()), b = Double.parseDouble(value.substring(split + 1).trim());
                    return new Amount(Math.min(a, b), Math.max(a, b));
                }
                double exact = Double.parseDouble(value); return new Amount(exact, exact);
            } catch (NumberFormatException ignored) { return new Amount(1.0, 1.0); }
        }
        double roll() { return min == max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max)); }
        private static int rangeSeparator(String value) { for (int i = 1; i < value.length() - 1; i++) if (value.charAt(i) == '-') return i; return -1; }
    }

    private interface NumericExpression {
        double eval(double level);
        static NumericExpression compile(String source) { return new ExpressionParser(source == null || source.isBlank() ? "level * 100" : source).parse(); }
    }

    private static final class ExpressionParser {
        private final String source; private int index;
        private ExpressionParser(String source) { this.source = source; }
        NumericExpression parse() { NumericExpression value = expression(); whitespace(); if (index != source.length()) throw new IllegalArgumentException("Unexpected token in curve: " + source.substring(index)); return value; }
        private NumericExpression expression() {
            NumericExpression left = term();
            while (true) { whitespace(); if (take('+')) { NumericExpression a=left,b=term(); left=l->a.eval(l)+b.eval(l); } else if (take('-')) { NumericExpression a=left,b=term(); left=l->a.eval(l)-b.eval(l); } else return left; }
        }
        private NumericExpression term() {
            NumericExpression left = power();
            while (true) { whitespace(); if (take('*')) { NumericExpression a=left,b=power(); left=l->a.eval(l)*b.eval(l); } else if (take('/')) { NumericExpression a=left,b=power(); left=l->a.eval(l)/b.eval(l); } else if (take('%')) { NumericExpression a=left,b=power(); left=l->a.eval(l)%b.eval(l); } else return left; }
        }
        private NumericExpression power() { NumericExpression left=unary(); whitespace(); if(!take('^'))return left; NumericExpression right=power(); return l->Math.pow(left.eval(l),right.eval(l)); }
        private NumericExpression unary() { whitespace(); if(take('+'))return unary(); if(take('-')){NumericExpression v=unary(); return l->-v.eval(l);} return primary(); }
        private NumericExpression primary() {
            whitespace(); if(take('(')){NumericExpression v=expression(); whitespace(); if(!take(')'))throw new IllegalArgumentException("Missing ')' in curve "+source); return v;}
            if(source.regionMatches(true,index,"level",0,5)){index+=5; return l->l;}
            int start=index; while(index<source.length()&&(Character.isDigit(source.charAt(index))||source.charAt(index)=='.'))index++;
            if(start==index)throw new IllegalArgumentException("Expected number or level in curve "+source);
            double number=Double.parseDouble(source.substring(start,index)); return l->number;
        }
        private void whitespace(){while(index<source.length()&&Character.isWhitespace(source.charAt(index)))index++;}
        private boolean take(char c){if(index<source.length()&&source.charAt(index)==c){index++;return true;}return false;}
    }
}
