package vn.svframe.lively.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import vn.svframe.lively.api.LivelyApi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Geometry-first discovery for buildings made by players instead of worldgen.
 *
 * <p>The scanner starts from a walkable indoor cell, flood-fills connected floor space, follows doors/gates,
 * ordinary one-block elevation changes and bounded vertical connectors, requires headroom/support/roof, then
 * classifies the shell from reloadable block/tag/capability rules. It is command-driven and bounded so discovery
 * never becomes a whole-world tick scan.</p>
 */
public final class BuiltStructureDiscovery {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int ROOF_SEARCH = 16;
    private static final int SEED_SEARCH = 2;
    private static final int MAX_HORIZONTAL_RADIUS = 32;
    private static final int MAX_VERTICAL_SPAN = 32;
    private static final int MAX_VERTICAL_CONNECTOR_REACH = 8;
    private static final int MAX_INTERIOR_CELLS = 8192;
    private static final int MIN_INTERIOR_CELLS = 4;
    private static final int MAX_OPEN_EXTERIOR_EDGES = 2;

    private static final Set<String> AUTO_CAPABILITIES = Set.of(
            "storage", "read", "teach", "smelt", "smith", "cook", "craft", "gather", "brew", "repair", "farm",
            "utility", "sleep", "entrance", "openable", "indoor", "shelter", "player_built", "residential");
    private static final Set<String> AUTO_POINTS = Set.of(
            "entrance", "bed", "storage", "gather", "lectern", "furnace", "brewing", "workstation", "farm");

    public enum Status {
        SUCCESS,
        REFRESHED,
        ALREADY_REGISTERED,
        NO_INTERIOR,
        OPEN_TO_OUTSIDE,
        TOO_SMALL,
        TOO_LARGE
    }

    public record Result(Status status, SemanticStructureRegistry.Structure structure, int interiorCells,
                         int exteriorOpenEdges, String detail) {
        public boolean success() {
            return status == Status.SUCCESS || status == Status.REFRESHED || status == Status.ALREADY_REGISTERED;
        }
    }

    private BuiltStructureDiscovery() {}

    public static Result discoverAndRegister(ServerWorld world, BlockPos requested) {
        String worldKey = world.getRegistryKey().getValue().toString();
        List<SemanticStructureRegistry.Structure> existing = LivelyApi.structures().at(
                worldKey, requested.getX() + .5D, requested.getY() + .5D, requested.getZ() + .5D);
        if (!existing.isEmpty()) {
            SemanticStructureRegistry.Structure structure = existing.getFirst();
            if (structure.capabilities().contains("player_built")) return discover(world, requested, structure);
            return new Result(Status.ALREADY_REGISTERED, structure, 0, 0,
                    "registered structure is manual/world-defined; automatic rescan left it unchanged");
        }
        return discover(world, requested, null);
    }

    /** Re-detects geometry/type for a previously discovered player-built building while preserving its stable ID. */
    public static Result rescanAndRegister(ServerWorld world, BlockPos requested) {
        String worldKey = world.getRegistryKey().getValue().toString();
        SemanticStructureRegistry.Structure existing = LivelyApi.structures().at(
                worldKey, requested.getX() + .5D, requested.getY() + .5D, requested.getZ() + .5D)
                .stream().findFirst().orElse(null);
        if (existing != null && !existing.capabilities().contains("player_built")) {
            return new Result(Status.ALREADY_REGISTERED, existing, 0, 0,
                    "registered structure is manual/world-defined; automatic rescan left it unchanged");
        }
        return discover(world, requested, existing);
    }

    private static Result discover(ServerWorld world, BlockPos requested, SemanticStructureRegistry.Structure existing) {
        ScanGeometry geometry = scan(world, requested);
        if (geometry.status != Status.SUCCESS) {
            return new Result(geometry.status, existing, geometry.interior.size(), geometry.exteriorOpenEdges, geometry.detail);
        }

        SemanticStructureRegistry.Structure structure = materialize(world, geometry, existing);
        structure = LivelyApi.structures().register(structure);
        Status status = existing == null ? Status.SUCCESS : Status.REFRESHED;
        return new Result(status, structure, geometry.interior.size(), geometry.exteriorOpenEdges,
                (existing == null ? "discovered " : "refreshed ") + structure.type() + " from "
                        + geometry.interior.size() + " indoor cells");
    }

    static String classify(Map<String, Integer> blockCounts, Map<String, Integer> capabilityCounts) {
        return BuiltStructureTypeRegistry.classify(blockCounts, capabilityCounts).type();
    }

    private static ScanGeometry scan(ServerWorld world, BlockPos requested) {
        HashMap<BlockPos, Integer> roofCache = new HashMap<>();
        BlockPos seed = resolveSeed(world, requested, roofCache);
        if (seed == null) return ScanGeometry.failure(Status.NO_INTERIOR,
                "no supported two-block-high indoor space with a roof nearby");

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<BlockPos> visited = new HashSet<>();
        LinkedHashSet<BlockPos> interior = new LinkedHashSet<>();
        queue.add(seed);
        visited.add(seed);

        int minX = seed.getX(), maxX = seed.getX();
        int minY = seed.getY(), maxY = seed.getY();
        int minZ = seed.getZ(), maxZ = seed.getZ();
        int maxRoofY = seed.getY() + 2;
        int exteriorOpenEdges = 0;

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (horizontalDistance(seed, current) > MAX_HORIZONTAL_RADIUS
                    || Math.abs(current.getY() - seed.getY()) > MAX_VERTICAL_SPAN) {
                return ScanGeometry.failure(Status.TOO_LARGE, "building exceeded bounded discovery radius");
            }
            int roofY = roofY(world, current, roofCache);
            if (roofY < 0 || !isWalkable(world, current, roofCache)) continue;

            interior.add(current);
            if (interior.size() > MAX_INTERIOR_CELLS) {
                return ScanGeometry.failure(Status.TOO_LARGE,
                        "building interior exceeded " + MAX_INTERIOR_CELLS + " cells");
            }
            minX = Math.min(minX, current.getX()); maxX = Math.max(maxX, current.getX());
            minY = Math.min(minY, current.getY()); maxY = Math.max(maxY, current.getY());
            minZ = Math.min(minZ, current.getZ()); maxZ = Math.max(maxZ, current.getZ());
            maxRoofY = Math.max(maxRoofY, roofY);

            for (Direction direction : HORIZONTAL) {
                BlockPos next = current.offset(direction);
                if (isWalkable(world, next, roofCache)) {
                    enqueue(visited, queue, next);
                    continue;
                }

                BlockState boundary = world.getBlockState(next);
                if (isHorizontalConnector(boundary)) {
                    BlockPos beyond = next.offset(direction);
                    for (int dy : new int[]{0, 1, -1}) {
                        BlockPos candidate = beyond.add(0, dy, 0);
                        if (isWalkable(world, candidate, roofCache)) {
                            enqueue(visited, queue, candidate);
                            break;
                        }
                    }
                    continue;
                }

                boolean stepped = false;
                for (int dy : new int[]{1, -1}) {
                    BlockPos candidate = next.add(0, dy, 0);
                    if (isWalkable(world, candidate, roofCache)) {
                        enqueue(visited, queue, candidate);
                        stepped = true;
                        break;
                    }
                }
                if (!stepped && isWalkableWithoutRoof(world, next) && roofY(world, next, roofCache) < 0) {
                    exteriorOpenEdges++;
                    if (exteriorOpenEdges > MAX_OPEN_EXTERIOR_EDGES) {
                        return ScanGeometry.failure(Status.OPEN_TO_OUTSIDE,
                                "interior opens directly to the outside; add walls/doors or scan from a more enclosed room");
                    }
                }
            }
            enqueueVerticalLandings(world, current, roofCache, visited, queue);
        }

        if (interior.size() < MIN_INTERIOR_CELLS) {
            return ScanGeometry.failure(Status.TOO_SMALL,
                    "interior is smaller than " + MIN_INTERIOR_CELLS + " walkable cells");
        }
        return new ScanGeometry(Status.SUCCESS, interior, minX, minY, minZ, maxX, maxY, maxZ, maxRoofY,
                exteriorOpenEdges, "ok");
    }

    private static void enqueueVerticalLandings(ServerWorld world, BlockPos current, Map<BlockPos, Integer> roofCache,
                                                Set<BlockPos> visited, ArrayDeque<BlockPos> queue) {
        if (!hasVerticalConnectorNear(world, current)) return;
        for (int sign : new int[]{1, -1}) {
            for (int distance = 2; distance <= MAX_VERTICAL_CONNECTOR_REACH; distance++) {
                BlockPos landing = current.add(0, sign * distance, 0);
                if (isWalkable(world, landing, roofCache)) {
                    enqueue(visited, queue, landing);
                    break;
                }
                if (distance > 2 && !hasVerticalConnectorNear(world, landing)) break;
            }
        }
    }

    private static boolean hasVerticalConnectorNear(ServerWorld world, BlockPos pos) {
        if (isVerticalConnector(world.getBlockState(pos)) || isVerticalConnector(world.getBlockState(pos.up()))
                || isVerticalConnector(world.getBlockState(pos.down()))) return true;
        for (Direction direction : HORIZONTAL) {
            if (isVerticalConnector(world.getBlockState(pos.offset(direction)))
                    || isVerticalConnector(world.getBlockState(pos.offset(direction).up()))) return true;
        }
        return false;
    }

    private static SemanticStructureRegistry.Structure materialize(ServerWorld world, ScanGeometry geometry,
                                                                    SemanticStructureRegistry.Structure existing) {
        int minX = geometry.minX - 1, minY = geometry.minY - 1, minZ = geometry.minZ - 1;
        int maxX = geometry.maxX + 1, maxY = Math.max(geometry.maxY + 2, geometry.maxRoofY), maxZ = geometry.maxZ + 1;

        HashMap<String, Integer> blockCounts = new HashMap<>();
        HashMap<String, Integer> tagCounts = new HashMap<>();
        HashMap<String, Integer> capabilityCounts = new HashMap<>();
        HashSet<String> capabilities = new HashSet<>();
        HashMap<String, String> points = new HashMap<>();
        ArrayList<BlockPos> connectors = new ArrayList<>();
        Map<String, TagKey<Block>> requiredTags = requiredTagKeys();

        if (existing != null) {
            Set<String> managed = BuiltStructureTypeRegistry.managedCapabilities();
            existing.capabilities().stream()
                    .filter(value -> !AUTO_CAPABILITIES.contains(value) && !managed.contains(value))
                    .forEach(capabilities::add);
            existing.points().forEach((key, value) -> {
                if (!AUTO_POINTS.contains(key) && !key.startsWith("work_")) points.put(key, value);
            });
        }

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = world.getBlockState(cursor);
                    String blockId = Registries.BLOCK.getId(state.getBlock()).toString().toLowerCase(Locale.ROOT);
                    blockCounts.merge(blockId, 1, Integer::sum);
                    for (var entry : requiredTags.entrySet()) {
                        if (state.isIn(entry.getValue())) tagCounts.merge(entry.getKey(), 1, Integer::sum);
                    }
                    Set<String> blockCapabilities = LivelyApi.blockCapabilities().capabilities(blockId);
                    for (String capability : blockCapabilities) {
                        capabilities.add(capability);
                        capabilityCounts.merge(capability, 1, Integer::sum);
                        points.putIfAbsent(pointName(capability), point(cursor));
                    }
                    if (isHorizontalConnector(state)) connectors.add(cursor.toImmutable());
                }
            }
        }

        capabilities.add("indoor");
        capabilities.add("shelter");
        capabilities.add("player_built");
        if (!connectors.isEmpty()) {
            BlockPos center = new BlockPos((geometry.minX + geometry.maxX) / 2, geometry.minY,
                    (geometry.minZ + geometry.maxZ) / 2);
            connectors.stream().min(Comparator.comparingInt(pos -> manhattan(pos, center)))
                    .ifPresent(pos -> points.put("entrance", point(pos)));
        }

        BuiltStructureTypeRegistry.Match match = BuiltStructureTypeRegistry.classify(blockCounts, tagCounts, capabilityCounts);
        String type = match.type();
        capabilities.addAll(match.addCapabilities());
        String id = existing == null
                ? uniqueId(type, (minX + maxX) / 2, minY, (minZ + maxZ) / 2)
                : existing.id();
        SemanticStructureRegistry.Bounds bounds = new SemanticStructureRegistry.Bounds(
                world.getRegistryKey().getValue().toString(), minX, minY, minZ, maxX, maxY, maxZ);
        return new SemanticStructureRegistry.Structure(id, type, bounds, capabilities, points,
                existing == null ? null : existing.parentId(), existing == null ? null : existing.townId(),
                existing == null ? SemanticStructureRegistry.OperationalState.OPEN : existing.state(), 0L);
    }

    private static Map<String, TagKey<Block>> requiredTagKeys() {
        HashMap<String, TagKey<Block>> result = new HashMap<>();
        for (String configured : BuiltStructureTypeRegistry.requiredTags()) {
            String raw = configured.startsWith("#") ? configured.substring(1) : configured;
            Identifier id = Identifier.tryParse(raw);
            if (id != null) result.put(configured, TagKey.of(RegistryKeys.BLOCK, id));
        }
        return Map.copyOf(result);
    }

    private static BlockPos resolveSeed(ServerWorld world, BlockPos requested, Map<BlockPos, Integer> roofCache) {
        ArrayList<BlockPos> candidates = new ArrayList<>();
        for (int radius = 0; radius <= SEED_SEARCH; radius++) {
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        candidates.add(requested.add(dx, dy, dz));
                    }
                }
            }
        }
        return candidates.stream().filter(pos -> isWalkable(world, pos, roofCache)).findFirst().orElse(null);
    }

    private static boolean isWalkable(ServerWorld world, BlockPos pos, Map<BlockPos, Integer> roofCache) {
        return isWalkableWithoutRoof(world, pos) && roofY(world, pos, roofCache) >= 0;
    }

    private static boolean isWalkableWithoutRoof(ServerWorld world, BlockPos pos) {
        return isOpen(world, pos) && isOpen(world, pos.up()) && isSupported(world, pos);
    }

    private static boolean isOpen(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean isSupported(ServerWorld world, BlockPos feet) {
        BlockPos floor = feet.down();
        return !world.getBlockState(floor).getCollisionShape(world, floor).isEmpty();
    }

    private static int roofY(ServerWorld world, BlockPos feet, Map<BlockPos, Integer> cache) {
        Integer cached = cache.get(feet);
        if (cached != null) return cached;
        for (int dy = 2; dy <= ROOF_SEARCH; dy++) {
            BlockPos pos = feet.up(dy);
            BlockState state = world.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) continue;
            if (!state.getCollisionShape(world, pos).isEmpty()) {
                cache.put(feet, pos.getY());
                return pos.getY();
            }
        }
        cache.put(feet, -1);
        return -1;
    }

    private static boolean isHorizontalConnector(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        return path.endsWith("_door") || path.endsWith("_fence_gate");
    }

    private static boolean isVerticalConnector(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        return path.equals("ladder") || path.equals("scaffolding") || path.equals("vine")
                || path.endsWith("_trapdoor");
    }

    private static void enqueue(Set<BlockPos> visited, ArrayDeque<BlockPos> queue, BlockPos pos) {
        BlockPos immutable = pos.toImmutable();
        if (visited.add(immutable)) queue.addLast(immutable);
    }

    private static int horizontalDistance(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static String point(BlockPos pos) { return pos.getX() + "," + pos.getY() + "," + pos.getZ(); }

    private static String pointName(String capability) {
        return switch (capability) {
            case "entrance", "openable" -> "entrance";
            case "sleep" -> "bed";
            case "storage" -> "storage";
            case "gather" -> "gather";
            case "read", "teach" -> "lectern";
            case "smelt", "cook" -> "furnace";
            case "brew" -> "brewing";
            case "smith", "repair" -> "workstation";
            case "farm" -> "farm";
            default -> "work_" + capability;
        };
    }

    private static String uniqueId(String type, int x, int y, int z) {
        String base = "built_" + type.toLowerCase(Locale.ROOT) + "_" + x + "_" + y + "_" + z;
        String id = base;
        int suffix = 2;
        while (LivelyApi.structures().get(id).isPresent()) id = base + "_" + suffix++;
        return id;
    }

    private record ScanGeometry(Status status, LinkedHashSet<BlockPos> interior,
                                int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int maxRoofY,
                                int exteriorOpenEdges, String detail) {
        private static ScanGeometry failure(Status status, String detail) {
            return new ScanGeometry(status, new LinkedHashSet<>(), 0, 0, 0, 0, 0, 0, 0, 0, detail);
        }
    }
}
