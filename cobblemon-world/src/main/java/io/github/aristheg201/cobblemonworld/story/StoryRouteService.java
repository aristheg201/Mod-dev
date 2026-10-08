package io.github.aristheg201.cobblemonworld.story;

import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Retain old flags/rewards while rebuilding the current chapter from actual authored milestones. */
public final class StoryRouteService {
    private static final List<String> CHAPTERS = List.of("prologue", "chapter_01_signal", "chapter_02_under_mountain",
            "chapter_03_house_of_cards", "chapter_04_kings_gambit", "chapter_05_league_fault",
            "chapter_06_seventh_lock", "chapter_07_false_victory", "battle_tower", "royal_league",
            "school_of_wolf", "chapter_08_last_person");
    private StoryRouteService() {}
    public static void migrate(ServerPlayer player) {
        var p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (p.storyRouteSchema >= 2) return;
        String old = p.currentStory;
        p.currentStory = "complete";
        for (String id : CHAPTERS) {
            var chapter = ContentRegistry.INSTANCE.chapter(id);
            if (chapter == null) throw new IllegalStateException("Missing route chapter " + id);
            boolean complete = true;
            for (String flag : chapter.completionFlags()) if (!p.storyFlags.contains(flag)) { complete = false; break; }
            if (!complete) { p.currentStory = id; p.currentObjective = chapter.objective(); break; }
            p.storyFlags.add("story_route_complete:" + id);
        }
        p.storyRouteSchema = 2;
        ProgressionStore.INSTANCE.save();
        CobblemonWorldMod.LOGGER.info("Story route migration for {}: {} -> {}; existing flags and rewards retained", player.getUUID(), old, p.currentStory);
    }
}
