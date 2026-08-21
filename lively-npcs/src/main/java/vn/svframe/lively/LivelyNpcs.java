package vn.svframe.lively;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.admin.LivelyCommands;
import vn.svframe.lively.admin.SelectionWand;
import vn.svframe.lively.ai.NpcAutonomyService;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.crime.InvestigationService;
import vn.svframe.lively.dialogue.DialogueService;
import vn.svframe.lively.event.LivingWorldDirectorService;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.navigation.WorldNavigationService;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcDefinitionStore;
import vn.svframe.lively.npc.NpcRuntime;
import vn.svframe.lively.npc.PlayerModelBody;
import vn.svframe.lively.npc.VanillaEntityBody;
import vn.svframe.lively.persistence.LegacyWorldStateMigration;
import vn.svframe.lively.persistence.NpcStateRegistry;
import vn.svframe.lively.persistence.NpcStateStore;
import vn.svframe.lively.persistence.SimulationStateStore;
import vn.svframe.lively.persistence.WorldHistoryJournal;
import vn.svframe.lively.simulation.BusinessSimulationService;
import vn.svframe.lively.simulation.CausalSimulationService;
import vn.svframe.lively.simulation.FamilyProgressionService;
import vn.svframe.lively.skin.SkinConfig;
import vn.svframe.lively.skin.SkinResolver;
import vn.svframe.lively.world.StructureCapabilityScanner;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bootstrap plus one isolated runtime session per MinecraftServer instance. */
public final class LivelyNpcs implements ModInitializer {
    public static final String MOD_ID = "livelynpcs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final AtomicLong ticks = new AtomicLong();
    private final AtomicLong historySequence = new AtomicLong();
    private final DialogueService dialogueService = new DialogueService();
    private volatile CompletableFuture<Void> pendingAutosave = CompletableFuture.completedFuture(null);
    private volatile MinecraftServer activeServer;

    private NpcStateRegistry stateRegistry;
    private NpcRuntime npcRuntime;
    private WorldHistoryJournal historyJournal;
    private SimulationStateStore simulationStore;
    private SkinResolver skinResolver;
    private WorldNavigationService navigation;
    private NpcAutonomyService autonomy;
    private LivingWorldDirectorService director;
    private CausalSimulationService causalSimulation;
    private FamilyProgressionService familyProgression;
    private BusinessSimulationService businessSimulation;
    private StructureCapabilityScanner structureScanner;
    private WorldEventEngine.Listener historyListener;

    @Override
    public void onInitialize() {
        dialogueService.install();
        SelectionWand.install();
        LivelyCommands.install();

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            NpcRuntime runtime = npcRuntime;
            if (world.isClient || hand != Hand.MAIN_HAND || runtime == null || !(player instanceof ServerPlayerEntity serverPlayer)
                    || LivelyApi.dialogues() == null) return ActionResult.PASS;
            return runtime.interact(serverPlayer, entity.getUuid(), LivelyApi.dialogues()) ? ActionResult.SUCCESS : ActionResult.PASS;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            NpcRuntime runtime = npcRuntime;
            if (runtime != null && activeServer == server) runtime.onPlayerJoin(handler.player);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(this::startSession);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stopSession);
        ServerTickEvents.END_SERVER_TICK.register(this::tickSession);

        LOGGER.info("Lively NPCs bootstrap registered; runtime state is isolated per dedicated/integrated server session");
    }

    private synchronized void startSession(MinecraftServer server) {
        if (activeServer == server) return;
        if (activeServer != null) stopSession(activeServer);

        LivelyApi.resetServerSessionState();
        dialogueService.bindSession();
        ticks.set(0L);
        historySequence.set(0L);
        pendingAutosave = CompletableFuture.completedFuture(null);
        activeServer = server;

        Path globalConfig = FabricLoader.getInstance().getConfigDir().resolve("livelynpcs");
        Path worldData = server.getSavePath(WorldSavePath.ROOT).resolve("livelynpcs");

        try {
            if (LegacyWorldStateMigration.importIfNeeded(globalConfig, worldData)) {
                LOGGER.info("Imported legacy config-scoped Lively state into {}. Original files were retained.", worldData);
            }
            simulationStore = new SimulationStateStore(worldData.resolve("state").resolve("simulation.json"));
            simulationStore.load().ifPresent(this::restoreSimulation);

            stateRegistry = new NpcStateRegistry(new NpcStateStore(worldData.resolve("state").resolve("npcs")));
            LivelyApi.installStateRegistry(stateRegistry);
            stateRegistry.preloadAll().whenComplete((count, error) -> {
                if (error != null) LOGGER.error("Lively NPC state preload failed", error);
                else LOGGER.info("Lively NPC state preload completed: {} NPC states", count);
            });

            skinResolver = new SkinResolver(SkinConfig.load(globalConfig));
            npcRuntime = new NpcRuntime(new NpcDefinitionStore(worldData.resolve("npcs").resolve("npcs.tsv")), stateRegistry);
            npcRuntime.registerProvider(NpcDefinition.BodyType.PLAYER, definition -> new PlayerModelBody(definition.id(), skinResolver));
            npcRuntime.registerProvider(NpcDefinition.BodyType.VANILLA, definition -> new VanillaEntityBody(definition.id()));
            npcRuntime.load();
            LivelyApi.installNpcRuntime(npcRuntime);

            structureScanner = new StructureCapabilityScanner();
            LivelyApi.installStructureScanner(structureScanner);
            navigation = new WorldNavigationService(npcRuntime, LivelyApi.structures());
            LivelyApi.installWorldNavigation(navigation);
            autonomy = new NpcAutonomyService(npcRuntime, stateRegistry, navigation);
            LivelyApi.installAutonomy(autonomy);
            LivelyApi.installInvestigationService(new InvestigationService(LivelyApi.crime(), LivelyApi.social(), LivelyApi.actors(), stateRegistry));

            director = new LivingWorldDirectorService();
            causalSimulation = new CausalSimulationService();
            familyProgression = new FamilyProgressionService();
            businessSimulation = new BusinessSimulationService();

            historyJournal = new WorldHistoryJournal(worldData.resolve("history").resolve("world-history.lwh"), 128L * 1024L * 1024L);
            try {
                var history = historyJournal.readAll();
                history.stream().mapToLong(WorldHistoryJournal.Entry::sequence).max().ifPresent(historySequence::set);
                LivelyApi.chronicle().rebuild(history);
                LOGGER.info("Lively world history loaded: {} records, {} chronicle eras", history.size(), LivelyApi.chronicle().eras().size());
            } catch (IOException error) {
                LOGGER.error("Lively world history validation failed", error);
            }
            historyListener = new WorldEventEngine.Listener() {
                @Override public void onStarted(WorldEventEngine.WorldEvent event) { journal("event_started", event); }
                @Override public void onFinished(WorldEventEngine.WorldEvent event) {
                    LivelyApi.chronicle().record(event);
                    journal("event_finished", event);
                }
                @Override public void onCancelled(WorldEventEngine.WorldEvent event) { journal("event_cancelled", event); }
            };
            LivelyApi.events().addListener(historyListener);

            npcRuntime.restoreSpawned(server);
            LOGGER.info("Lively NPCs initialized: living-world runtime ready for dedicated and integrated singleplayer servers");
            LOGGER.info("Lively world-scoped state root: {}", worldData);
        } catch (RuntimeException error) {
            LOGGER.error("Lively server session startup failed", error);
            stopSession(server);
            throw error;
        }
    }

    private void tickSession(MinecraftServer server) {
        if (activeServer != server || npcRuntime == null || navigation == null || autonomy == null) return;
        long tick = ticks.incrementAndGet();
        npcRuntime.tick(server);
        structureScanner.tick(server);
        navigation.tick(server);
        autonomy.tick(server, tick);
        director.tick(tick);
        causalSimulation.tick(tick);
        familyProgression.tick(tick);
        businessSimulation.tick(tick);

        if (tick % 20L == 0L) {
            Instant now = Instant.now();
            LivelyApi.profiler().measure("world-events", () -> LivelyApi.events().advance(now));
            LivelyApi.profiler().measure("quest-expiry", () -> LivelyApi.quests().expire(now));
            LivelyApi.profiler().measure("rumor-expiry", () -> LivelyApi.social().expireRumors(now));
        }
        if (tick % 1200L == 0L) LivelyApi.profiler().measure("market-tick", () -> { LivelyApi.economy().marketTick(); return 0; });
        if (tick % 600L == 0L) npcRuntime.checkpoint();
        if (tick % 6000L != 0L || !pendingAutosave.isDone()) return;
        SimulationStateStore.Bundle bundle = captureSimulation();
        pendingAutosave = CompletableFuture.allOf(stateRegistry.saveAll(), simulationStore.saveAsync(bundle)).whenComplete((ignored, error) -> {
            if (error != null) LOGGER.error("Lively autosave failed", error);
        });
    }

    private synchronized void stopSession(MinecraftServer server) {
        if (activeServer != server) return;
        SimulationStateStore.Bundle bundle = simulationStore == null ? null : captureSimulation();
        try {
            if (historyListener != null) LivelyApi.events().removeListener(historyListener);
            if (director != null) director.close();
            if (causalSimulation != null) causalSimulation.close();
            if (autonomy != null) autonomy.close();
            if (navigation != null) navigation.close();
            if (npcRuntime != null) npcRuntime.shutdown(server);
            if (skinResolver != null) skinResolver.close();

            if (stateRegistry != null && simulationStore != null && bundle != null) {
                CompletableFuture.allOf(stateRegistry.saveAll(), simulationStore.saveAsync(bundle))
                        .orTimeout(10L, TimeUnit.SECONDS).join();
            } else if (stateRegistry != null) {
                stateRegistry.saveAll().orTimeout(10L, TimeUnit.SECONDS).join();
            }
        } catch (RuntimeException error) {
            LOGGER.error("Lively final state flush failed", error);
        } finally {
            dialogueService.reset();
            if (stateRegistry != null) stateRegistry.close();
            if (simulationStore != null) simulationStore.close();
            stateRegistry = null; npcRuntime = null; historyJournal = null; simulationStore = null; skinResolver = null;
            navigation = null; autonomy = null; director = null; causalSimulation = null; familyProgression = null;
            businessSimulation = null; structureScanner = null; historyListener = null;
            pendingAutosave = CompletableFuture.completedFuture(null);
            activeServer = null;
            LivelyApi.resetServerSessionState();
            LOGGER.info("Lively server session stopped and world-scoped runtime state was released");
        }
    }

    private SimulationStateStore.Bundle captureSimulation() {
        return new SimulationStateStore.Bundle(
                LivelyApi.structures().snapshot(), LivelyApi.social().snapshot(), LivelyApi.romance().snapshot(),
                LivelyApi.family().snapshot(), LivelyApi.crime().snapshot(), LivelyApi.economy().snapshot(),
                LivelyApi.factions().snapshot(), LivelyApi.quests().snapshot(), LivelyApi.schedules().snapshot(),
                LivelyApi.storyArcs().snapshot(), LivelyApi.storySeeds().snapshot(), LivelyApi.events().snapshot());
    }

    private void restoreSimulation(SimulationStateStore.Bundle bundle) {
        try {
            if (bundle.structures() != null) LivelyApi.structures().restore(bundle.structures());
            if (bundle.social() != null) LivelyApi.social().restore(bundle.social());
            if (bundle.romance() != null) LivelyApi.romance().restore(bundle.romance());
            if (bundle.family() != null) LivelyApi.family().restore(bundle.family());
            if (bundle.crime() != null) LivelyApi.crime().restore(bundle.crime());
            if (bundle.economy() != null) LivelyApi.economy().restore(bundle.economy());
            if (bundle.factions() != null) LivelyApi.factions().restore(bundle.factions());
            if (bundle.quests() != null) LivelyApi.quests().restore(bundle.quests());
            if (bundle.schedules() != null) LivelyApi.schedules().restore(bundle.schedules());
            if (bundle.storyArcs() != null) LivelyApi.storyArcs().restore(bundle.storyArcs());
            if (bundle.storySeeds() != null) LivelyApi.storySeeds().restore(bundle.storySeeds());
            if (bundle.events() != null) LivelyApi.events().restore(bundle.events());
            LOGGER.info("Lively simulation state restored");
        } catch (RuntimeException error) {
            LOGGER.error("Lively simulation state restore failed", error);
        }
    }

    private void journal(String type, WorldEventEngine.WorldEvent event) {
        WorldHistoryJournal journal = historyJournal;
        if (journal == null) return;
        try {
            Map<String, String> facts = new HashMap<>();
            facts.put("category", event.category().name());
            facts.put("seed", event.seed());
            facts.put("phase", event.phase().name());
            facts.put("structure", event.structureId() == null ? "" : event.structureId());
            facts.put("intensity", Double.toString(event.intensity()));
            String kind = event.facts().get("kind");
            if (kind != null && !kind.isBlank()) facts.put("kind", kind);
            String eraBreak = event.facts().get("era_break");
            if (eraBreak != null) facts.put("era_break", eraBreak);
            journal.append(new WorldHistoryJournal.Entry(historySequence.incrementAndGet(), Instant.now(), type, event.id().toString(), facts));
        } catch (IOException error) {
            LOGGER.error("Failed to append Lively world history record {}", type, error);
        }
    }
}
