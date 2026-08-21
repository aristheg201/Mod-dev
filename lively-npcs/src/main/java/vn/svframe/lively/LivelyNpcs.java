package vn.svframe.lively;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.svframe.lively.admin.LivelyCommands;
import vn.svframe.lively.admin.SelectionWand;
import vn.svframe.lively.ai.NpcAutonomyService;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.dialogue.DialogueService;
import vn.svframe.lively.event.LivingWorldDirectorService;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.navigation.WorldNavigationService;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcDefinitionStore;
import vn.svframe.lively.npc.NpcRuntime;
import vn.svframe.lively.npc.PlayerModelBody;
import vn.svframe.lively.npc.VanillaEntityBody;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class LivelyNpcs implements ModInitializer {
    public static final String MOD_ID = "livelynpcs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final AtomicLong ticks = new AtomicLong();
    private final AtomicLong historySequence = new AtomicLong();
    private volatile CompletableFuture<Void> pendingAutosave = CompletableFuture.completedFuture(null);

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

    @Override
    public void onInitialize() {
        Path config = FabricLoader.getInstance().getConfigDir().resolve("livelynpcs");
        simulationStore = new SimulationStateStore(config.resolve("state").resolve("simulation.json"));
        simulationStore.load().ifPresent(this::restoreSimulation);

        director = new LivingWorldDirectorService();
        causalSimulation = new CausalSimulationService();
        familyProgression = new FamilyProgressionService();
        businessSimulation = new BusinessSimulationService();
        structureScanner = new StructureCapabilityScanner();
        LivelyApi.installStructureScanner(structureScanner);

        stateRegistry = new NpcStateRegistry(new NpcStateStore(config.resolve("state")));
        LivelyApi.installStateRegistry(stateRegistry);
        stateRegistry.preloadAll().whenComplete((count, error) -> {
            if (error != null) LOGGER.error("Lively NPC state preload failed", error);
            else LOGGER.info("Lively NPC state preload completed: {} NPC states", count);
        });

        skinResolver = new SkinResolver(SkinConfig.load(config));
        npcRuntime = new NpcRuntime(new NpcDefinitionStore(config.resolve("npcs").resolve("npcs.tsv")), stateRegistry);
        npcRuntime.registerProvider(NpcDefinition.BodyType.PLAYER, definition -> new PlayerModelBody(definition.id(), skinResolver));
        npcRuntime.registerProvider(NpcDefinition.BodyType.VANILLA, definition -> new VanillaEntityBody(definition.id()));
        npcRuntime.load();
        LivelyApi.installNpcRuntime(npcRuntime);

        navigation = new WorldNavigationService(npcRuntime, LivelyApi.structures());
        LivelyApi.installWorldNavigation(navigation);
        autonomy = new NpcAutonomyService(npcRuntime, stateRegistry, navigation);
        LivelyApi.installAutonomy(autonomy);

        historyJournal = new WorldHistoryJournal(config.resolve("history").resolve("world-history.lwh"), 128L * 1024L * 1024L);
        try {
            var history = historyJournal.readAll();
            history.stream().mapToLong(WorldHistoryJournal.Entry::sequence).max().ifPresent(historySequence::set);
            LOGGER.info("Lively world history loaded: {} records", history.size());
        } catch (IOException error) {
            LOGGER.error("Lively world history validation failed", error);
        }
        LivelyApi.events().addListener(new WorldEventEngine.Listener() {
            @Override public void onStarted(WorldEventEngine.WorldEvent event) { journal("event_started", event); }
            @Override public void onFinished(WorldEventEngine.WorldEvent event) { journal("event_finished", event); }
            @Override public void onCancelled(WorldEventEngine.WorldEvent event) { journal("event_cancelled", event); }
        });

        DialogueService dialogueService = new DialogueService();
        dialogueService.install();
        SelectionWand.install();
        LivelyCommands.install();

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer) || LivelyApi.dialogues() == null) return ActionResult.PASS;
            return npcRuntime.interact(serverPlayer, entity.getUuid(), LivelyApi.dialogues()) ? ActionResult.SUCCESS : ActionResult.PASS;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> npcRuntime.onPlayerJoin(handler.player));
        ServerLifecycleEvents.SERVER_STARTED.register(npcRuntime::restoreSpawned);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
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
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SimulationStateStore.Bundle bundle = captureSimulation();
            causalSimulation.close();
            autonomy.close();
            navigation.close();
            npcRuntime.shutdown(server);
            skinResolver.close();
            try {
                CompletableFuture.allOf(stateRegistry.saveAll(), simulationStore.saveAsync(bundle)).orTimeout(10L, TimeUnit.SECONDS).join();
            } catch (RuntimeException ex) {
                LOGGER.error("Lively final state flush failed", ex);
            } finally {
                stateRegistry.close();
                simulationStore.close();
            }
        });
        LOGGER.info("Lively NPCs initialized: living-world runtime ready for dedicated and integrated singleplayer servers");
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
        try {
            historyJournal.append(new WorldHistoryJournal.Entry(historySequence.incrementAndGet(), Instant.now(), type, event.id().toString(),
                    Map.of("category", event.category().name(), "seed", event.seed(), "phase", event.phase().name(),
                            "structure", event.structureId() == null ? "" : event.structureId())));
        } catch (IOException error) {
            LOGGER.error("Failed to append Lively world history record {}", type, error);
        }
    }
}
