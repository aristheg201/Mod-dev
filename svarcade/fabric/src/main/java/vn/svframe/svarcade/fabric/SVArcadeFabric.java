package vn.svframe.svarcade.fabric;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import vn.svframe.svarcade.bot.BotRuntime;
import vn.svframe.svarcade.config.*;
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
    private volatile long tick;
    private volatile Set<String> integrations = Set.of();
    private volatile Path definitionsPath;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> start());
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SVArcadeCommands.register(dispatcher, this));
    }

    private void start() {
        if (!starting.compareAndSet(false, true)) throw new IllegalStateException("SVArcade already starting");
        thread = new ThreadGuard();
        io = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), runnable -> {
            Thread worker = new Thread(runnable, "svarcade-io"); worker.setDaemon(true); return worker;
        }, new ThreadPoolExecutor.AbortPolicy());
        bots = new BotRuntime(thread, new Registry<>(Map.of()), 2, 128);
        SystemCatalog catalog = StandardRuntimeCatalog.create(bots,
                session -> (actor, currentTick) -> new IntentGate.Facts(actor, currentTick, 0, true));
        definitions = new DefinitionRegistry(); loader = new DefinitionLoader(catalog.schemas());
        runtime = new GenericGameRuntime(thread, definitions, catalog.factories(), 128);
        integrations = IntegrationDetector.available();
        definitionsPath = FabricLoader.getInstance().getConfigDir().resolve("svarcade").resolve("minigames");
        CompletableFuture.supplyAsync(() -> DefaultInstaller.install(definitionsPath), io)
                .thenCompose(created -> definitions.reload(() -> loader.loadAll(definitionsPath), integrations, io).thenApply(result -> Map.entry(created, result)))
                .whenComplete((entry, failure) -> {
                    if (failure != null) LOG.log(System.Logger.Level.ERROR, "SVArcade definition bootstrap failed", failure);
                    else LOG.log(System.Logger.Level.INFO, "SVArcade loaded definitions; defaults created={0}, applied={1}, generation={2}",
                            entry.getKey(), entry.getValue().applied(), entry.getValue().generation());
                });
    }

    private void tick() {
        ThreadGuard guard = thread; GenericGameRuntime value = runtime;
        if (guard == null || value == null) return; guard.check(); value.tick(++tick);
    }

    /** Transactional reload: invalid candidates leave the previous registry live. */
    CompletableFuture<DefinitionRegistry.ReloadResult> reload() {
        ExecutorService executor = io; DefinitionRegistry registry = definitions; DefinitionLoader currentLoader = loader; Path path = definitionsPath;
        if (executor == null || registry == null || currentLoader == null || path == null) return CompletableFuture.failedFuture(new IllegalStateException("SVArcade not started"));
        return registry.reload(() -> currentLoader.loadAll(path), integrations, executor);
    }
    GenericGameRuntime runtime() { return runtime; }
    DefinitionRegistry definitions() { return definitions; }
    BotRuntime bots() { return bots; }
    long currentTick() { return tick; }

    private void stop() {
        ThreadGuard guard = thread; if (guard == null) return; guard.check();
        GenericGameRuntime sessions = runtime; if (sessions != null) sessions.closeAll();
        BotRuntime workers = bots; if (workers != null) workers.close();
        ExecutorService executor = io; if (executor != null) executor.shutdownNow();
        runtime = null; bots = null; loader = null; definitions = null; io = null; definitionsPath = null; integrations = Set.of(); thread = null; tick = 0; starting.set(false);
    }
}
