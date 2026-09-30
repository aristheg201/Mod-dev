package vn.svarcade.tcg.fabric;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Allocates isolated physical coliseum cells inside the dedicated Duel Realm. */
public final class DuelRealmService {
    private static final Logger LOG = LoggerFactory.getLogger("cardworlds-duel-realm");
    public static final RegistryKey<World> KEY =
        RegistryKey.of(RegistryKeys.WORLD, Identifier.of("svarcade_tcg", "duel_realm"));

    public record Arena(String matchId, int index, BlockPos center) {}
    private record ReturnPoint(RegistryKey<World> world, double x, double y, double z, float yaw, float pitch) {}

    private final MinecraftServer server;
    private final Map<String,Arena> live = new LinkedHashMap<>();
    private final Map<UUID,ReturnPoint> returns = new HashMap<>();
    private final Set<Integer> built = new HashSet<>();
    private final PriorityQueue<Integer> free = new PriorityQueue<>();
    private int nextIndex;

    public DuelRealmService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server);
    }

    public ServerWorld world() {
        ServerWorld world = server.getWorld(KEY);
        if (world == null) throw new IllegalStateException("Duel Realm dimension is not loaded.");
        return world;
    }

    public Arena allocate(String matchId) {
        return live.computeIfAbsent(matchId, id -> {
            int index = free.isEmpty() ? nextIndex++ : free.remove();
            int col = index % 24;
            int row = index / 24;
            BlockPos center = new BlockPos(
                col * DuelColiseumStructure.ARENA_SPACING,
                DuelColiseumStructure.FLOOR_Y,
                row * DuelColiseumStructure.ARENA_SPACING
            );

            if (built.add(index)) {
                long started = System.nanoTime();
                LOG.info("CARDWORLDS_DUEL_REALM_BUILD_START match={} cell={} center={}", id, index, center);
                DuelColiseumStructure.build(world(), center);
                LOG.info("CARDWORLDS_DUEL_REALM_BUILD_DONE match={} cell={} ms={}",
                    id, index, (System.nanoTime() - started) / 1_000_000L);
            }
            return new Arena(id, index, center);
        });
    }

    public void release(Arena arena) {
        if (arena == null) return;
        Arena removed = live.remove(arena.matchId());
        if (removed != null) free.add(removed.index());
    }

    public void enterDuelist(ServerPlayerEntity player, Arena arena, int seat) {
        saveReturn(player);
        BlockPos c = arena.center();
        boolean south = seat == 0;
        double z = c.getZ() + (south ? 21.5 : -21.5);
        float yaw = south ? 180f : 0f;
        LOG.info("CARDWORLDS_DUEL_REALM_TELEPORT_DUELIST player={} match={} seat={}",
            player.getName().getString(), arena.matchId(), seat);
        teleport(player, c.getX() + 0.5, c.getY() + 4.0, z, yaw, 18f);
    }

    public void enterSpectator(ServerPlayerEntity player, Arena arena, int slot) {
        saveReturn(player);
        BlockPos c = arena.center();
        int lane = Math.floorMod(slot, 4);
        boolean west = ((slot / 4) & 1) == 0;
        double x = c.getX() + (west ? -27.5 : 27.5);
        double z = c.getZ() + (-9 + lane * 6) + 0.5;
        float yaw = west ? -90f : 90f;
        LOG.info("CARDWORLDS_DUEL_REALM_TELEPORT_SPECTATOR player={} match={} slot={}",
            player.getName().getString(), arena.matchId(), slot);
        teleport(player, x, c.getY() + 7.0, z, yaw, 16f);
    }

    public void restore(ServerPlayerEntity player) {
        ReturnPoint point = returns.remove(player.getUuid());
        if (point == null) return;
        ServerWorld target = server.getWorld(point.world());
        if (target == null) target = server.getOverworld();
        player.teleport(target, point.x(), point.y(), point.z(), Set.of(), point.yaw(), point.pitch());
        player.setVelocity(0, 0, 0);
    }

    public String status() {
        return "loaded=" + (server.getWorld(KEY) != null)
            + ", live_arenas=" + live.size()
            + ", built_cells=" + built.size()
            + ", saved_returns=" + returns.size();
    }

    private void saveReturn(ServerPlayerEntity player) {
        if (player.getServerWorld().getRegistryKey().equals(KEY)) return;
        returns.computeIfAbsent(player.getUuid(), id -> new ReturnPoint(
            player.getServerWorld().getRegistryKey(),
            player.getX(), player.getY(), player.getZ(),
            player.getYaw(), player.getPitch()
        ));
    }

    private void teleport(ServerPlayerEntity player, double x, double y, double z, float yaw, float pitch) {
        player.teleport(world(), x, y, z, Set.of(), yaw, pitch);
        player.setVelocity(0, 0, 0);
    }
}
