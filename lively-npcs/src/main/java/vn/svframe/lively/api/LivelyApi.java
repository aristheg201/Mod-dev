package vn.svframe.lively.api;

import vn.svframe.lively.combat.CombatCortex;
import vn.svframe.lively.dialogue.DialogueService;
import vn.svframe.lively.navigation.NavigationCortex;
import vn.svframe.lively.persistence.NpcStateRegistry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LivelyApi {
    private static final CopyOnWriteArrayList<CombatAdapter> COMBAT = new CopyOnWriteArrayList<>();
    private static final CombatCortex COMBAT_CORTEX = new CombatCortex();
    private static final NavigationCortex NAVIGATION_CORTEX = new NavigationCortex();
    private static volatile DialogueService dialogues;
    private static volatile NpcStateRegistry states;

    private LivelyApi() {}

    public static CombatCortex combat() { return COMBAT_CORTEX; }
    public static NavigationCortex navigation() { return NAVIGATION_CORTEX; }
    public static List<CombatAdapter> combatAdapters() { return List.copyOf(COMBAT); }
    public static void registerCombatAdapter(CombatAdapter adapter) { COMBAT.addIfAbsent(adapter); }
    public static DialogueService dialogues() { return dialogues; }
    public static void installDialogueService(DialogueService service) { dialogues = service; }
    public static NpcStateRegistry states() { return states; }
    public static void installStateRegistry(NpcStateRegistry registry) { states = registry; }
}
