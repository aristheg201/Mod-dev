package vn.svframe.svarcade.fabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import vn.svframe.svarcade.bot.BotRuntime;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.persistence.*;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.IntentGate;
import vn.svframe.svarcade.systems.StandardRuntimeCatalog;

/** Server-only Fabric bootstrap. All authoritative runtime mutation stays on the Minecraft server thread. */
public final class SVArcadeFabric implements ModInitializer {
    private static final System.Logger LOG = System.getLogger("SVArcade");
    private final AtomicBoolean starting = new AtomicBoolean();
    private volatile ExecutorService io;
    private volatile DefinitionRegistry definitions;
    private volatile DefinitionLoader loader;
    private volatile GenericGameRuntime runtime;
    private volatile BotRuntime bots;
    private volatile ThreadGuard thread;
    private volatile PlatformIntegrations platform;
    private volatile PlayerStateProtection playerState;
    private volatile PersistenceManager persistence;
    private volatile RecoveryManager recovery;
    private volatile long tick;
    private volatile Set<String> integrations = Set.of();
    private volatile Path configRoot;
    private volatile Path definitionsPath;

    private record Bootstrap(int defaultsCreated, IntegrationSettings settings, AtomicStore stateStore) { }

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> restorePending(handler.player.getUuid()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SVArcadeCommands.register(dispatcher, this));
    }

    private void start(MinecraftServer server) {
        if (!starting.compareAndSet(false, true)) throw new IllegalStateException("SVArcade already starting");
        thread = new ThreadGuard();
        configRoot = FabricLoader.getInstance().getConfigDir().resolve("svarcade"); definitionsPath = configRoot.resolve("minigames");
        io = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            Thread worker = new Thread(runnable, "svarcade-io"); worker.setDaemon(true); return worker;
        }, new ThreadPoolExecutor.AbortPolicy());
        ExecutorService executor = io; Path root = configRoot;
        CompletableFuture.supplyAsync(() -> {
            try {
                int created = DefaultInstaller.install(root); IntegrationSettings settings = IntegrationSettings.load(root.resolve("integrations"));
                return new Bootstrap(created, settings, new AtomicStore(root.resolve("state"), 128));
            } catch (IOException failure) { throw new CompletionException(failure); }
        }, executor).whenComplete((bootstrap, failure) -> server.execute(() -> {
            if (!starting.get() || io != executor) { if (bootstrap != null) bootstrap.stateStore().close(); return; }
            if (failure != null) { LOG.log(System.Logger.Level.ERROR, "SVArcade configuration bootstrap failed", failure); starting.set(false); return; }
            finishStart(server, bootstrap);
        }));
    }

    private void finishStart(MinecraftServer server, Bootstrap bootstrap) {
        ThreadGuard guard = thread; if (guard == null) { bootstrap.stateStore().close(); return; } guard.check();
        playerState = new PlayerStateProtection(guard, new FabricPlayerStateBridge(server), 4096);
        platform = PlatformIntegrations.discover(server, bootstrap.settings(), playerState); integrations = platform.capabilities();
        bots = new BotRuntime(guard, new Registry<>(Map.of()), 2, 128);
        SystemCatalog catalog = StandardRuntimeCatalog.create(bots,
                session -> (actor, currentTick) -> new IntentGate.Facts(actor, currentTick, 0, true), platform.rewardProviders());
        definitions = new DefinitionRegistry(); loader = new DefinitionLoader(catalog.schemas());
        runtime = new GenericGameRuntime(guard, definitions, catalog.factories(), 128, platform::initializeSession); platform.bindRuntime(runtime);
        persistence = new PersistenceManager(guard, bootstrap.stateStore(), 32); recovery = new RecoveryManager(guard, bootstrap.stateStore(), 4096);
        if (!platform.unavailable().isEmpty()) LOG.log(System.Logger.Level.INFO, "SVArcade optional adapters unavailable: {0}", platform.unavailable());
        ExecutorService executor = io; DefinitionRegistry registry = definitions; DefinitionLoader currentLoader = loader; Path path = definitionsPath;
        registry.reload(() -> currentLoader.loadAll(path), integrations, executor).whenComplete((result, failure) -> {
            if (failure != null) { LOG.log(System.Logger.Level.ERROR, "SVArcade definition bootstrap failed", failure); return; }
            RecoveryManager manager = recovery; if (manager == null) return;
            manager.scan().whenComplete((scan, scanFailure) -> server.execute(() -> {
                if (!starting.get() || recovery != manager) return;
                if (scanFailure != null) LOG.log(System.Logger.Level.ERROR, "SVArcade recovery scan failed", scanFailure);
                else {
                    RecoveryManager.Applied applied = manager.apply(runtime, persistence, playerState, scan);
                    for (UUID sessionId : applied.recovered()) {
                        GenericSession session = runtime.sessions().get(sessionId); if (session != null) platform.bindRecoveredState(session, applied.protectedOwners());
                    }
                    restoreUnownedOnline(applied.protectedOwners());
                    if (!applied.aborted().isEmpty()) LOG.log(System.Logger.Level.WARNING, "SVArcade recovery aborted states: {0}", applied.aborted());
                    LOG.log(System.Logger.Level.INFO, "SVArcade loaded definitions; defaults created={0}, applied={1}, generation={2}, recovered={3}",
                            bootstrap.defaultsCreated(), result.applied(), result.generation(), applied.recovered().size());
                }
            }));
        });
    }

    private void restoreUnownedOnline(Map<UUID,UUID> owners) {
        PlayerStateProtection protection = playerState; GenericGameRuntime sessions = runtime; if (protection == null || sessions == null) return;
        Set<UUID> liveSessions = sessions.sessions().keySet();
        for (UUID player : new ArrayList<>(protection.snapshots().keySet())) {
            UUID owner = owners.get(player); if ((owner == null || !liveSessions.contains(owner)) && serverPlayerOnline(player)) protection.restore(player);
        }
    }

    private boolean serverPlayerOnline(UUID player) {
        PlatformIntegrations adapters = platform; if (adapters == null) return false;
        GenericGameRuntime value = runtime; if (value == null) return false;
        return value.sessions().values().stream().flatMap(session -> session.participants().keySet().stream()).anyMatch(player::equals)
                || playerState != null && playerState.protectedPlayer(player); // JOIN handler performs actual online test through bridge
    }

    private void restorePending(UUID player) {
        PlayerStateProtection protection = playerState; GenericGameRuntime sessions = runtime;
        if (protection == null || sessions == null || !protection.protectedPlayer(player)) return;
        boolean active = sessions.sessions().values().stream().anyMatch(session -> session.status() == GenericSession.Status.RUNNING && session.participants().containsKey(player));
        if (!active && !protection.restore(player)) LOG.log(System.Logger.Level.WARNING, "SVArcade pending player-state restore failed for {0}", player);
    }

    private void tick() {
        ThreadGuard guard = thread; GenericGameRuntime value = runtime;
        if (guard == null || value == null) return; guard.check(); value.tick(++tick);
        PersistenceManager manager = persistence; if (manager != null) manager.tick(value.sessions().values(), playerState, 8);
    }

    CompletableFuture<DefinitionRegistry.ReloadResult> reload() {
        ExecutorService executor = io; DefinitionRegistry registry = definitions; DefinitionLoader currentLoader = loader; Path path = definitionsPath;
        if (executor == null || registry == null || currentLoader == null || path == null) return CompletableFuture.failedFuture(new IllegalStateException("SVArcade not started"));
        return registry.reload(() -> currentLoader.loadAll(path), integrations, executor);
    }
    GenericGameRuntime runtime() { return runtime; }
    DefinitionRegistry definitions() { return definitions; }
    BotRuntime bots() { return bots; }
    PlatformIntegrations platform() { return platform; }
    PersistenceManager persistence() { return persistence; }
    PlayerStateProtection playerState() { return playerState; }
    long currentTick() { return tick; }

    private void stop() {
        ThreadGuard guard = thread; if (guard == null) return; guard.check(); starting.set(false);
        GenericGameRuntime sessions = runtime; if (sessions != null) sessions.closeAll();
        PlayerStateProtection protection = playerState; if (protection != null) protection.restoreAll();
        PersistenceManager state = persistence; if (state != null) { state.tick(List.of(), protection, 4096); state.close(); }
        PlatformIntegrations adapters = platform; if (adapters != null) adapters.close();
        BotRuntime workers = bots; if (workers != null) workers.close();
        ExecutorService executor = io; if (executor != null) executor.shutdownNow();
        runtime = null; bots = null; loader = null; definitions = null; io = null; platform = null; playerState = null; persistence = null; recovery = null;
        configRoot = null; definitionsPath = null; integrations = Set.of(); thread = null; tick = 0;
    }
}
