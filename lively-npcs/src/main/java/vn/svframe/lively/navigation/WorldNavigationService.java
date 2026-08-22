package vn.svframe.lively.navigation;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcRuntime;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Main-thread budgeted world sampling, worker A*, main-thread body movement. Never breaks or places blocks. */
public final class WorldNavigationService implements AutoCloseable {
    public enum Mode { GOTO, FOLLOW, ESCORT, FLEE, SCHEDULE, GROUP }
    public record Status(UUID npcId, Mode mode, String world, Vec3d target, int remainingNodes, String phase, String reason) {}

    private static final int SAMPLE_COLUMNS_PER_TICK = 384;
    private static final int MAX_COLUMNS = 18_000;
    private static final int MAX_AXIS = 128;
    private static final int MAX_Y_SPAN = 16;
    private static final int REPATH_INTERVAL = 30;
    private static final int CACHE_TTL_TICKS = 100;
    private static final int MAX_CACHE = 512;
    private static final double DEFAULT_SPEED = 0.16D;

    private static final int FLAG_SWIM = 1;
    private static final int FLAG_HAZARD = 1 << 1;

    private final NpcRuntime npcs;
    private final SemanticStructureRegistry structures;
    private final ExecutorService workers;
    private final ConcurrentHashMap<UUID, Task> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey, CachedPath> cache = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong();

    public WorldNavigationService(NpcRuntime npcs, SemanticStructureRegistry structures) {
        this.npcs = Objects.requireNonNull(npcs);
        this.structures = Objects.requireNonNull(structures);
        int threads = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 3));
        workers = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "Lively-Navigation-Worker"); thread.setDaemon(true); return thread;
        });
    }

    public boolean goTo(UUID id, String world, Vec3d target) { return begin(id, Mode.GOTO, world, target, null, 1.25D); }
    public boolean flee(UUID id, String world, Vec3d threat, double distance) {
        Vec3d current = npcs.position(id).orElse(null);
        if (current == null) return false;
        Vec3d away = current.subtract(threat);
        if (away.lengthSquared() < 0.01D) away = new Vec3d(1D, 0D, 0D);
        return begin(id, Mode.FLEE, world, current.add(away.normalize().multiply(Math.max(4D, Math.min(48D, distance)))), null, 1D);
    }
    public boolean follow(UUID id, UUID target) {
        NpcDefinition definition = npcs.get(id).orElse(null);
        return definition != null && begin(id, Mode.FOLLOW, definition.world(), new Vec3d(definition.x(), definition.y(), definition.z()), target, 2.4D);
    }
    public boolean escort(UUID id, UUID target) {
        NpcDefinition definition = npcs.get(id).orElse(null);
        return definition != null && begin(id, Mode.ESCORT, definition.world(), new Vec3d(definition.x(), definition.y(), definition.z()), target, 2.2D);
    }
    public boolean goToStructure(UUID id, String structureId) {
        var structure = structures.get(structureId).orElse(null);
        if (structure == null) return false;
        Vec3d point = structurePoint(structure, "entrance").orElseGet(() -> center(structure.bounds()));
        return begin(id, Mode.SCHEDULE, structure.bounds().world(), point, null, 1.2D);
    }

    /** Formation offsets avoid stacking every group member onto exactly one block. */
    public int groupGoTo(List<UUID> npcIds, String world, Vec3d target, double spacing) {
        double safeSpacing = Math.max(0.8D, Math.min(4D, spacing));
        int accepted = 0;
        List<UUID> ids = npcIds.stream().distinct().limit(32).toList();
        for (int i = 0; i < ids.size(); i++) {
            int row = (i + 1) / 2;
            double side = i == 0 ? 0D : (i % 2 == 0 ? 1D : -1D) * row * safeSpacing;
            double back = i == 0 ? 0D : -row * safeSpacing;
            Vec3d offsetTarget = target.add(side, 0D, back);
            if (begin(ids.get(i), Mode.GROUP, world, offsetTarget, null, 1.4D)) accepted++;
        }
        return accepted;
    }

    public boolean stop(UUID id) { return tasks.remove(id) != null; }
    public Optional<Status> status(UUID id) {
        Task task = tasks.get(id);
        return task == null ? Optional.empty() : Optional.of(new Status(id, task.mode, task.world, task.target,
                task.path == null ? 0 : Math.max(0, task.path.size() - task.pathIndex), task.phase, task.reason));
    }
    public int activeCount() { return tasks.size(); }

    public void tick(MinecraftServer server) {
        long tick = server.getTicks();
        if (tick % 200L == 0L) evictCache(tick);
        for (Task task : List.copyOf(tasks.values())) {
            if (task.cancelled || npcs.get(task.npcId).isEmpty()) { tasks.remove(task.npcId, task); continue; }
            updateDynamicTarget(server, task, tick);
            if (task.builder != null) { sample(server, task); continue; }
            if (task.planning || tick < task.retryAt) continue;
            if (task.path == null || task.pathIndex >= task.path.size()) {
                if (nearTarget(task)) {
                    if (task.mode == Mode.FOLLOW || task.mode == Mode.ESCORT) continue;
                    task.phase = "arrived"; tasks.remove(task.npcId, task); continue;
                }
                startSnapshot(server, task);
                continue;
            }
            moveAlongPath(server, task, tick);
        }
    }

    private boolean begin(UUID id, Mode mode, String world, Vec3d target, UUID tracked, double stopDistance) {
        NpcDefinition definition = npcs.get(id).orElse(null);
        if (definition == null || !definition.spawned() || !definition.aiEnabled()) return false;
        Task task = new Task(id, revision.incrementAndGet(), mode, world, target, tracked, stopDistance);
        tasks.put(id, task);
        return true;
    }

    private void updateDynamicTarget(MinecraftServer server, Task task, long tick) {
        if (task.trackedEntity == null || tick - task.lastTargetRefresh < 10L) return;
        task.lastTargetRefresh = tick;
        Vec3d position = null; String world = null;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(task.trackedEntity);
        if (player != null) {
            position = player.getPos(); world = player.getServerWorld().getRegistryKey().getValue().toString();
        }
        if (position == null) {
            position = npcs.position(task.trackedEntity).orElse(null);
            world = npcs.worldKey(task.trackedEntity).orElse(null);
        }
        if (position == null || world == null) { task.reason = "tracked_target_unavailable"; return; }
        boolean changed = !world.equals(task.world) || position.squaredDistanceTo(task.target) > 4D;
        task.target = position; task.world = world;
        if (changed && !nearTarget(task) && tick - task.lastRepath >= REPATH_INTERVAL) {
            task.path = null; task.builder = null; task.planning = false; task.lastRepath = tick;
            task.noPathFailures = 0; task.retryAt = 0L; task.reason = "target_moved"; task.phase = "repath";
        }
    }

    private void startSnapshot(MinecraftServer server, Task task) {
        Vec3d current = npcs.position(task.npcId).orElse(null);
        String currentWorld = npcs.worldKey(task.npcId).orElse(null);
        if (current == null || currentWorld == null) { task.reason = "body_position_unavailable"; tasks.remove(task.npcId, task); return; }
        if (!currentWorld.equals(task.world)) { task.reason = "cross_world_navigation_requires_teleport"; tasks.remove(task.npcId, task); return; }
        ServerWorld world = world(server, task.world);
        if (world == null) { task.reason = "unknown_world"; tasks.remove(task.npcId, task); return; }

        Vec3d segment = segmentTarget(current, task.target);
        CacheKey cacheKey = CacheKey.of(task.world, current, segment);
        CachedPath cached = cache.get(cacheKey);
        if (cached != null && server.getTicks() - cached.createdTick <= CACHE_TTL_TICKS && validateCached(world, cached.path)) {
            task.path = cached.path; task.pathIndex = Math.min(1, cached.path.size() - 1); task.phase = "moving_cached"; task.reason = "";
            task.noPathFailures = 0; task.retryAt = 0L; return;
        }

        int minX = (int) Math.floor(Math.min(current.x, segment.x)) - 6;
        int maxX = (int) Math.floor(Math.max(current.x, segment.x)) + 6;
        int minZ = (int) Math.floor(Math.min(current.z, segment.z)) - 6;
        int maxZ = (int) Math.floor(Math.max(current.z, segment.z)) + 6;
        if (maxX - minX + 1 > MAX_AXIS || maxZ - minZ + 1 > MAX_AXIS || (maxX - minX + 1) * (maxZ - minZ + 1) > MAX_COLUMNS) {
            task.reason = "navigation_snapshot_too_large"; tasks.remove(task.npcId, task); return;
        }
        int minY = Math.max(world.getBottomY() + 1, (int) Math.floor(Math.min(current.y, segment.y)) - 5);
        int maxY = Math.min(world.getTopY() - 2, (int) Math.ceil(Math.max(current.y, segment.y)) + 5);
        if (maxY - minY + 1 > MAX_Y_SPAN) {
            int center = (int) Math.floor(current.y);
            minY = Math.max(world.getBottomY() + 1, center - MAX_Y_SPAN / 2);
            maxY = Math.min(world.getTopY() - 2, minY + MAX_Y_SPAN - 1);
        }
        task.builder = new SnapshotBuilder(task.revision, task.world, current, segment, cacheKey, minX, maxX, minZ, maxZ, minY, maxY);
        task.phase = "sampling"; task.reason = "";
    }

    private void sample(MinecraftServer server, Task task) {
        SnapshotBuilder builder = task.builder;
        if (builder == null) return;
        ServerWorld world = world(server, builder.world);
        if (world == null) { tasks.remove(task.npcId, task); return; }
        int budget = SAMPLE_COLUMNS_PER_TICK;
        while (budget-- > 0 && builder.cursorX <= builder.maxX) {
            int x = builder.cursorX, z = builder.cursorZ;
            for (int y = builder.minY; y <= builder.maxY; y++) {
                BlockPos feet = new BlockPos(x, y, z);
                NodeInfo info = classify(world, feet);
                if (info != null) builder.nodes.put(feet.asLong(), info);
            }
            builder.cursorZ++;
            if (builder.cursorZ > builder.maxZ) { builder.cursorZ = builder.minZ; builder.cursorX++; }
        }
        if (builder.cursorX <= builder.maxX) return;

        task.builder = null; task.planning = true; task.phase = "planning"; task.lastRepath = server.getTicks();
        WalkSnapshot snapshot = builder.freeze();
        long expected = task.revision;
        workers.execute(() -> {
            List<BlockPos> path = plan(snapshot);
            server.execute(() -> {
                Task current = tasks.get(task.npcId);
                if (current != task || current.revision != expected || current.cancelled) return;
                current.planning = false;
                if (path.isEmpty()) {
                    current.noPathFailures++;
                    current.reason = "no_path:" + current.noPathFailures;
                    if (NavigationRetryPolicy.abandon(current.mode, current.noPathFailures)) {
                        current.phase = "abandoned";
                        tasks.remove(current.npcId, current);
                    } else {
                        current.retryAt = server.getTicks() + NavigationRetryPolicy.delayTicks(current.noPathFailures);
                        current.phase = "backoff";
                    }
                    return;
                }
                current.path = path; current.pathIndex = Math.min(1, path.size() - 1); current.phase = "moving"; current.reason = "";
                current.noPathFailures = 0; current.retryAt = 0L;
                if (cache.size() < MAX_CACHE) cache.put(snapshot.cacheKey, new CachedPath(path, server.getTicks()));
            });
        });
    }

    private void moveAlongPath(MinecraftServer server, Task task, long tick) {
        Vec3d current = npcs.position(task.npcId).orElse(null);
        if (current == null) { tasks.remove(task.npcId, task); return; }
        BlockPos node = task.path.get(task.pathIndex);
        if (tick % 10L == 0L) {
            ServerWorld world = world(server, task.world);
            if (world == null || classify(world, node) == null) {
                task.path = null; task.reason = "path_invalidated"; task.phase = "repath"; task.retryAt = tick + REPATH_INTERVAL; return;
            }
        }
        Vec3d target = new Vec3d(node.getX() + 0.5D, node.getY(), node.getZ() + 0.5D);
        Vec3d delta = target.subtract(current);
        double distance = delta.length();
        if (distance < 0.17D) { task.pathIndex++; return; }
        double speed = navigationSpeed(task.npcId);
        Vec3d step = current.add(delta.normalize().multiply(Math.min(speed, distance)));
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
        if (!npcs.moveStep(server, task.npcId, task.world, step, yaw, pitch)) {
            task.reason = "body_move_rejected"; tasks.remove(task.npcId, task);
        }
    }

    private double navigationSpeed(UUID id) {
        return npcs.get(id).map(definition -> {
            try { return Math.max(0.04D, Math.min(0.45D, Double.parseDouble(definition.metadata().getOrDefault("navigation.speed", Double.toString(DEFAULT_SPEED))))); }
            catch (NumberFormatException ignored) { return DEFAULT_SPEED; }
        }).orElse(DEFAULT_SPEED);
    }

    private boolean nearTarget(Task task) {
        Vec3d current = npcs.position(task.npcId).orElse(null);
        String world = npcs.worldKey(task.npcId).orElse(null);
        return current != null && task.world.equals(world) && current.squaredDistanceTo(task.target) <= task.stopDistance * task.stopDistance;
    }

    private static Vec3d segmentTarget(Vec3d start, Vec3d goal) {
        Vec3d delta = goal.subtract(start);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal <= 80D) return goal;
        double scale = 80D / horizontal;
        return new Vec3d(start.x + delta.x * scale, start.y + delta.y * scale, start.z + delta.z * scale);
    }

    private static List<BlockPos> plan(WalkSnapshot snapshot) {
        long start = nearest(snapshot.nodes, snapshot.start), goal = nearest(snapshot.nodes, snapshot.goal);
        if (start == Long.MIN_VALUE || goal == Long.MIN_VALUE) return List.of();
        PriorityQueue<NodeScore> open = new PriorityQueue<>(Comparator.comparingDouble(NodeScore::f));
        Map<Long, Double> g = new HashMap<>(); Map<Long, Long> previous = new HashMap<>(); Set<Long> closed = new HashSet<>();
        g.put(start, 0D); open.add(new NodeScore(start, heuristic(start, goal)));
        int visited = 0; long deadline = System.nanoTime() + 20_000_000L;
        while (!open.isEmpty() && visited++ < 80_000 && System.nanoTime() < deadline) {
            long current = open.poll().pos;
            if (!closed.add(current)) continue;
            if (current == goal) return reconstruct(previous, current);
            BlockPos position = BlockPos.fromLong(current);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                long next = neighbor(snapshot.nodes, position, dx, dz);
                if (next == Long.MIN_VALUE || closed.contains(next)) continue;
                if (dx != 0 && dz != 0 && (!hasHorizontal(snapshot.nodes, position, dx, 0) || !hasHorizontal(snapshot.nodes, position, 0, dz))) continue;
                BlockPos n = BlockPos.fromLong(next);
                NodeInfo info = snapshot.nodes.get(next);
                int dy = n.getY() - position.getY();
                double cost = dx != 0 && dz != 0 ? 1.414D : 1D;
                if (dy > 0) cost += 0.45D;
                if (dy < 0) cost += Math.abs(dy) * 0.12D;
                if ((info.flags & FLAG_SWIM) != 0) cost += 0.65D;
                if ((info.flags & FLAG_HAZARD) != 0) cost += 8D;
                double tentative = g.getOrDefault(current, Double.POSITIVE_INFINITY) + cost;
                if (tentative < g.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    g.put(next, tentative); previous.put(next, current); open.add(new NodeScore(next, tentative + heuristic(next, goal)));
                }
            }
        }
        return List.of();
    }

    private static long neighbor(Map<Long, NodeInfo> nodes, BlockPos position, int dx, int dz) {
        for (int dy : new int[]{0, 1, -1, -2}) {
            long value = BlockPos.asLong(position.getX() + dx, position.getY() + dy, position.getZ() + dz);
            if (nodes.containsKey(value)) return value;
        }
        return Long.MIN_VALUE;
    }
    private static boolean hasHorizontal(Map<Long, NodeInfo> nodes, BlockPos position, int dx, int dz) {
        return neighbor(nodes, position, dx, dz) != Long.MIN_VALUE;
    }

    private static long nearest(Map<Long, NodeInfo> nodes, Vec3d position) {
        long best = Long.MIN_VALUE; double bestDistance = Double.POSITIVE_INFINITY;
        for (long packed : nodes.keySet()) {
            BlockPos p = BlockPos.fromLong(packed);
            double dx = p.getX() + 0.5D - position.x, dy = p.getY() - position.y, dz = p.getZ() + 0.5D - position.z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) { bestDistance = distance; best = packed; }
        }
        return bestDistance <= 64D ? best : Long.MIN_VALUE;
    }

    private static double heuristic(long a, long b) {
        BlockPos x = BlockPos.fromLong(a), y = BlockPos.fromLong(b);
        return Math.abs(x.getX() - y.getX()) + Math.abs(x.getZ() - y.getZ()) + Math.abs(x.getY() - y.getY()) * 0.4D;
    }

    private static List<BlockPos> reconstruct(Map<Long, Long> previous, long current) {
        ArrayDeque<BlockPos> result = new ArrayDeque<>(); result.addFirst(BlockPos.fromLong(current));
        while (previous.containsKey(current)) { current = previous.get(current); result.addFirst(BlockPos.fromLong(current)); }
        return List.copyOf(result);
    }

    private static NodeInfo classify(ServerWorld world, BlockPos feet) {
        BlockPos head = feet.up(), below = feet.down();
        if (!world.isInBuildLimit(feet) || !world.isInBuildLimit(head) || !world.isInBuildLimit(below)) return null;
        BlockState feetState = world.getBlockState(feet), headState = world.getBlockState(head), belowState = world.getBlockState(below);
        String feetId = Registries.BLOCK.getId(feetState.getBlock()).toString();
        String belowId = Registries.BLOCK.getId(belowState.getBlock()).toString();
        if (hardHazard(feetId) || hardHazard(belowId) || !world.getFluidState(feet).isEmpty() && world.getFluidState(feet).isIn(net.minecraft.registry.tag.FluidTags.LAVA)) return null;
        boolean swim = world.getFluidState(feet).isIn(net.minecraft.registry.tag.FluidTags.WATER);
        boolean emptyBody = feetState.getCollisionShape(world, feet).isEmpty() && headState.getCollisionShape(world, head).isEmpty();
        if (!emptyBody && !swim) return null;
        boolean support = !belowState.getCollisionShape(world, below).isEmpty() || swim;
        if (!support) return null;
        int flags = swim ? FLAG_SWIM : 0;
        if (softHazard(feetId) || softHazard(belowId)) flags |= FLAG_HAZARD;
        return new NodeInfo(flags);
    }

    private static boolean hardHazard(String id) {
        return id.equals("minecraft:fire") || id.equals("minecraft:soul_fire") || id.equals("minecraft:cactus") ||
                id.equals("minecraft:powder_snow") || id.equals("minecraft:campfire") || id.equals("minecraft:soul_campfire");
    }
    private static boolean softHazard(String id) {
        return id.equals("minecraft:magma_block") || id.equals("minecraft:sweet_berry_bush") || id.equals("minecraft:wither_rose");
    }

    private boolean validateCached(ServerWorld world, List<BlockPos> path) {
        if (path.isEmpty() || path.size() > 512) return false;
        for (int i = 0; i < path.size(); i += Math.max(1, path.size() / 16)) if (classify(world, path.get(i)) == null) return false;
        return classify(world, path.get(path.size() - 1)) != null;
    }

    private void evictCache(long tick) {
        cache.entrySet().removeIf(entry -> tick - entry.getValue().createdTick > CACHE_TTL_TICKS);
        if (cache.size() <= MAX_CACHE) return;
        cache.entrySet().stream().sorted(Comparator.comparingLong(entry -> entry.getValue().createdTick)).limit(cache.size() - MAX_CACHE)
                .map(Map.Entry::getKey).toList().forEach(cache::remove);
    }

    private static Optional<Vec3d> structurePoint(SemanticStructureRegistry.Structure structure, String key) {
        String raw = structure.points().get(key);
        if (raw == null) return Optional.empty();
        String[] parts = raw.split(",");
        if (parts.length != 3) return Optional.empty();
        try { return Optional.of(new Vec3d(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]))); }
        catch (NumberFormatException ignored) { return Optional.empty(); }
    }

    private static Vec3d center(SemanticStructureRegistry.Bounds bounds) {
        return new Vec3d((bounds.minX() + bounds.maxX() + 1) / 2D, bounds.minY() + 1D, (bounds.minZ() + bounds.maxZ() + 1) / 2D);
    }
    private static ServerWorld world(MinecraftServer server, String key) {
        Identifier id = Identifier.tryParse(key);
        return id == null ? null : server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, id));
    }

    @Override public void close() { workers.shutdownNow(); tasks.clear(); cache.clear(); }

    private static final class Task {
        final UUID npcId; final long revision; final Mode mode; final UUID trackedEntity; final double stopDistance;
        volatile String world; volatile Vec3d target; volatile SnapshotBuilder builder; volatile boolean planning, cancelled;
        volatile List<BlockPos> path; volatile int pathIndex; volatile long lastTargetRefresh, lastRepath, retryAt;
        volatile int noPathFailures;
        volatile String phase = "queued", reason = "";
        Task(UUID npcId, long revision, Mode mode, String world, Vec3d target, UUID trackedEntity, double stopDistance) {
            this.npcId = npcId; this.revision = revision; this.mode = mode; this.world = world; this.target = target;
            this.trackedEntity = trackedEntity; this.stopDistance = stopDistance;
        }
    }

    private static final class SnapshotBuilder {
        final long revision; final String world; final Vec3d start, goal; final CacheKey cacheKey;
        final int minX, maxX, minZ, maxZ, minY, maxY; final Map<Long, NodeInfo> nodes = new HashMap<>();
        int cursorX, cursorZ;
        SnapshotBuilder(long revision, String world, Vec3d start, Vec3d goal, CacheKey cacheKey,
                        int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
            this.revision = revision; this.world = world; this.start = start; this.goal = goal; this.cacheKey = cacheKey;
            this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ; this.minY = minY; this.maxY = maxY;
            this.cursorX = minX; this.cursorZ = minZ;
        }
        WalkSnapshot freeze() { return new WalkSnapshot(revision, world, start, goal, cacheKey, Map.copyOf(nodes)); }
    }

    private record NodeInfo(int flags) {}
    private record WalkSnapshot(long revision, String world, Vec3d start, Vec3d goal, CacheKey cacheKey, Map<Long, NodeInfo> nodes) {}
    private record NodeScore(long pos, double f) {}
    private record CachedPath(List<BlockPos> path, long createdTick) { CachedPath { path = List.copyOf(path); } }
    private record CacheKey(String world, int sx, int sy, int sz, int gx, int gy, int gz) {
        static CacheKey of(String world, Vec3d start, Vec3d goal) {
            return new CacheKey(world, (int) Math.floor(start.x), (int) Math.floor(start.y), (int) Math.floor(start.z),
                    (int) Math.floor(goal.x), (int) Math.floor(goal.y), (int) Math.floor(goal.z));
        }
    }
}
