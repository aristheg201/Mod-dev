package vn.svframe.lively.world;

import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import vn.svframe.lively.api.LivelyApi;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread, budgeted semantic scan. Reads blocks only and never mutates Minecraft world state. */
public final class StructureCapabilityScanner {
    private static final int BLOCKS_PER_TICK = 512;
    private static final long MAX_VOLUME = 2_000_000L;
    private static final int MAX_QUEUE = 64;

    public record Status(String structureId, long scanned, long total, String phase, Set<String> capabilities) {
        public Status { capabilities = Set.copyOf(capabilities); }
    }

    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final ConcurrentHashMap<String, Scan> scans = new ConcurrentHashMap<>();

    public synchronized boolean request(String structureId) {
        SemanticStructureRegistry.Structure structure = LivelyApi.structures().get(structureId).orElse(null);
        if (structure == null || structure.bounds().volume() > MAX_VOLUME || scans.containsKey(structureId) || queue.size() >= MAX_QUEUE) return false;
        Scan scan = new Scan(structure);
        scans.put(structureId, scan);
        queue.addLast(structureId);
        return true;
    }

    public Optional<Status> status(String structureId) {
        Scan scan = scans.get(structureId);
        if (scan == null) return Optional.empty();
        return Optional.of(new Status(structureId, scan.scanned, scan.total, scan.phase, scan.capabilities));
    }

    public int activeCount() { return scans.size(); }

    public void tick(MinecraftServer server) {
        String id;
        synchronized (this) { id = queue.peekFirst(); }
        if (id == null) return;
        Scan scan = scans.get(id);
        if (scan == null) { synchronized (this) { queue.pollFirst(); } return; }
        ServerWorld world = world(server, scan.structure.bounds().world());
        if (world == null) { complete(scan, "unknown_world", false); return; }

        int budget = BLOCKS_PER_TICK;
        while (budget-- > 0 && !scan.done()) {
            BlockPos pos = new BlockPos(scan.x, scan.y, scan.z);
            Identifier blockId = Registries.BLOCK.getId(world.getBlockState(pos).getBlock());
            Set<String> capabilities = LivelyApi.blockCapabilities().capabilities(blockId.toString());
            if (!capabilities.isEmpty()) {
                scan.capabilities.addAll(capabilities);
                for (String capability : capabilities) scan.points.putIfAbsent(pointName(capability), pos.toImmutable());
            }
            scan.scanned++;
            scan.advance();
        }
        if (scan.done()) complete(scan, "complete", true);
    }

    private void complete(Scan scan, String phase, boolean apply) {
        scan.phase = phase;
        if (apply) {
            LivelyApi.structures().addCapabilities(scan.structure.id(), scan.capabilities);
            scan.points.forEach((name, pos) -> LivelyApi.structures().setPointIfAbsent(scan.structure.id(), name,
                    pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
        }
        scans.remove(scan.structure.id());
        synchronized (this) {
            if (scan.structure.id().equals(queue.peekFirst())) queue.pollFirst();
            else queue.remove(scan.structure.id());
        }
    }

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

    private static ServerWorld world(MinecraftServer server, String raw) {
        Identifier id = Identifier.tryParse(raw);
        return id == null ? null : server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, id));
    }

    private static final class Scan {
        final SemanticStructureRegistry.Structure structure;
        final long total;
        final HashSet<String> capabilities = new HashSet<>();
        final HashMap<String, BlockPos> points = new HashMap<>();
        int x, y, z;
        long scanned;
        String phase = "scanning";

        Scan(SemanticStructureRegistry.Structure structure) {
            this.structure = structure;
            SemanticStructureRegistry.Bounds bounds = structure.bounds();
            this.total = bounds.volume();
            this.x = bounds.minX(); this.y = bounds.minY(); this.z = bounds.minZ();
        }

        boolean done() { return x > structure.bounds().maxX(); }
        void advance() {
            SemanticStructureRegistry.Bounds bounds = structure.bounds();
            z++;
            if (z > bounds.maxZ()) { z = bounds.minZ(); y++; }
            if (y > bounds.maxY()) { y = bounds.minY(); x++; }
        }
    }
}
