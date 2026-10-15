package svperf.trainerbattle;

import kiwiapollo.cobblemontrainerbattle.battle.BattleHistory;
import kiwiapollo.cobblemontrainerbattle.battle.BattleHistoryStorage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-authoritative incremental persistence for Cobblemon Trainer Battle.
 * NBT snapshots run on the server tick thread, *only for changed players*.
 * File compression and writes run on exactly one bounded I/O worker.
 */
public final class DirtySaveController {
    private static final Logger LOG = LoggerFactory.getLogger("TrainerBattle-SVPerf");
    private static final Map<BattleHistory, UUID> OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ConcurrentHashMap<UUID, Long> DIRTY = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final int INTERVAL_TICKS = 20 * 5;
    private static final int MAX_PER_BATCH = 8;
    private static final int MAX_PENDING_WRITES = 64;
    private static final AtomicInteger WRITE_FAILURES = new AtomicInteger();
    private static volatile ThreadPoolExecutor writer;
    private static volatile Field storageField;
    private static volatile Method toNbtMethod;

    private DirtySaveController() {}

    public static void attach(BattleHistory history, UUID player) {
        if (history != null && player != null) {
            OWNERS.put(history, player);
        }
    }

    public static void changed(BattleHistory history) {
        UUID player = OWNERS.get(history);
        if (player != null) {
            // Revision guards against losing mutations made while a snapshot is queued.
            DIRTY.merge(player, 1L, Long::sum);
        } else {
            LOG.warn("Unbound Trainer Battle history was modified; data will be handled at shutdown");
        }
    }

    public static void started() {
        OWNERS.clear();
        DIRTY.clear();
        IN_FLIGHT.clear();
        WRITE_FAILURES.set(0);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % INTERVAL_TICKS != 0 || DIRTY.isEmpty()) {
            return;
        }
        ThreadPoolExecutor pool = io();
        Path dir = path(server);
        int queued = 0;
        for (Map.Entry<UUID, Long> entry : DIRTY.entrySet()) {
            if (queued >= MAX_PER_BATCH || pool.getQueue().remainingCapacity() == 0) {
                break;
            }
            UUID player = entry.getKey();
            long revision = entry.getValue();
            if (!IN_FLIGHT.add(player)) {
                continue;
            }
            try {
                NbtCompound snapshot = snapshot(player);
                if (snapshot == null) {
                    DIRTY.remove(player, revision);
                    IN_FLIGHT.remove(player);
                    continue;
                }
                pool.execute(() -> {
                    try {
                        if (write(player, snapshot, dir)) {
                            // Do not clear newer edits made after this snapshot.
                            DIRTY.remove(player, revision);
                        }
                    } finally {
                        IN_FLIGHT.remove(player);
                    }
                });
                queued++;
            } catch (Exception e) {
                IN_FLIGHT.remove(player);
                LOG.error("Could not prepare incremental Trainer Battle save for {}", player, e);
                break; // Preserve dirty state for the next retry.
            }
        }
    }

    public static void stopped(MinecraftServer server) {
        // Shutdown is the sole point at which waiting for disk I/O is acceptable.
        ThreadPoolExecutor pool = writer;
        if (pool != null) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(120, TimeUnit.SECONDS)) {
                    LOG.error("Trainer Battle save worker did not terminate after 120 seconds");
                    pool.shutdownNow();
                    if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                        LOG.error("Cannot safely flush over an active writer; original .dat files remain intact");
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Interrupted while waiting for Trainer Battle history I/O", e);
                return;
            }
        }

        int flushed = 0;
        Path dir = path(server);
        // No more gameplay ticks occur here. Persist only records still dirty,
        // including ones changed while previous async writes were in flight.
        for (Map.Entry<UUID, Long> entry : DIRTY.entrySet()) {
            UUID player = entry.getKey();
            try {
                NbtCompound tag = snapshot(player);
                if (tag != null && write(player, tag, dir)) {
                    DIRTY.remove(player, entry.getValue());
                    flushed++;
                }
            } catch (Exception e) {
                LOG.error("Final dirty-only Trainer Battle save failed for {}", player, e);
            }
        }
        if (!DIRTY.isEmpty()) {
            LOG.error("{} Trainer Battle player histories could not be persisted", DIRTY.size());
        }
        LOG.info("Trainer Battle shutdown: {} final changed histories flushed; no full rewrite", flushed);
        writer = null;
        IN_FLIGHT.clear();
    }

    @SuppressWarnings("unchecked")
    private static NbtCompound snapshot(UUID player) throws Exception {
        Field field = storageField;
        Method method = toNbtMethod;
        if (field == null || method == null) {
            synchronized (DirtySaveController.class) {
                field = storageField;
                if (field == null) {
                    field = BattleHistoryStorage.class.getDeclaredField("storage");
                    field.setAccessible(true);
                    storageField = field;
                }
                method = toNbtMethod;
                if (method == null) {
                    method = BattleHistoryStorage.class.getDeclaredMethod("toNbtCompound", Map.class);
                    method.setAccessible(true);
                    toNbtMethod = method;
                }
            }
        }
        Map<UUID, Map<Identifier, BattleHistory>> all =
                (Map<UUID, Map<Identifier, BattleHistory>>) field.get(null);
        Map<Identifier, BattleHistory> data = all.get(player);
        return data == null ? null : (NbtCompound) method.invoke(BattleHistoryStorage.getInstance(), data);
    }

    private static synchronized ThreadPoolExecutor io() {
        if (writer == null || writer.isShutdown()) {
            ThreadFactory factory = job -> {
                Thread thread = new Thread(job, "TrainerBattle-Dirty-IO");
                thread.setDaemon(true);
                return thread;
            };
            writer = new ThreadPoolExecutor(
                    1, 1, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(MAX_PENDING_WRITES), factory,
                    new ThreadPoolExecutor.AbortPolicy());
        }
        return writer;
    }

    private static Path path(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT)
                .resolve("cobblemontrainerbattle").resolve("battle_history");
    }

    private static boolean write(UUID player, NbtCompound tag, Path directory) {
        Path target = directory.resolve(player + ".dat");
        Path backup = directory.resolve(player + ".dat_old");
        Path temp = directory.resolve(player + ".dat.tmp");
        try {
            Files.createDirectories(directory);
            NbtIo.writeCompressed(tag, temp);
            if (Files.exists(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            WRITE_FAILURES.incrementAndGet();
            LOG.error("Incremental async Trainer Battle save failed for {}", player, e);
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }
            return false;
        }
    }
}
