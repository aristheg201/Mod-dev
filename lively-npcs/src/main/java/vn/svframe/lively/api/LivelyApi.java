package vn.svframe.lively.api;

import vn.svframe.lively.actor.ActorRegistry;
import vn.svframe.lively.combat.CombatCortex;
import vn.svframe.lively.dialogue.DialogueService;
import vn.svframe.lively.event.StoryDirector;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.navigation.NavigationCortex;
import vn.svframe.lively.persistence.NpcStateRegistry;
import vn.svframe.lively.simulation.SimulationLodController;
import vn.svframe.lively.world.SemanticStructureRegistry;
import vn.svframe.lively.world.WorldMutationPolicy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LivelyApi {
    private static final CopyOnWriteArrayList<CombatAdapter> COMBAT = new CopyOnWriteArrayList<>();
    private static final CombatCortex COMBAT_CORTEX = new CombatCortex();
    private static final NavigationCortex NAVIGATION_CORTEX = new NavigationCortex();
    private static final ActorRegistry ACTORS = new ActorRegistry();
    private static final SemanticStructureRegistry STRUCTURES = new SemanticStructureRegistry();
    private static final WorldMutationPolicy WORLD_MUTATIONS = WorldMutationPolicy.secureDefaults();
    private static final WorldEventEngine EVENTS = new WorldEventEngine(STRUCTURES, WORLD_MUTATIONS, 128);
    private static final StoryDirector STORY = new StoryDirector();
    private static final SimulationLodController LOD = new SimulationLodController();
    private static volatile DialogueService dialogues;
    private static volatile NpcStateRegistry states;

    private LivelyApi() {}

    public static CombatCortex combat() { return COMBAT_CORTEX; }
    public static NavigationCortex navigation() { return NAVIGATION_CORTEX; }
    public static ActorRegistry actors() { return ACTORS; }
    public static SemanticStructureRegistry structures() { return STRUCTURES; }
    public static WorldMutationPolicy worldMutations() { return WORLD_MUTATIONS; }
    public static WorldEventEngine events() { return EVENTS; }
    public static StoryDirector story() { return STORY; }
    public static SimulationLodController simulationLod() { return LOD; }
    public static List<CombatAdapter> combatAdapters() { return List.copyOf(COMBAT); }
    public static void registerCombatAdapter(CombatAdapter adapter) { COMBAT.addIfAbsent(adapter); }
    public static DialogueService dialogues() { return dialogues; }
    public static void installDialogueService(DialogueService service) { dialogues = service; }
    public static NpcStateRegistry states() { return states; }
    public static void installStateRegistry(NpcStateRegistry registry) { states = registry; }
}
