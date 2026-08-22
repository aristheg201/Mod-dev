package vn.svframe.lively.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.event.WorldEventEngine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeConfigServiceTest {
    @TempDir Path temp;

    @Test
    void createsSafeDefaultsAndReloadsLiveBudgets() throws Exception {
        Path file = temp.resolve("runtime.properties");
        RuntimeConfigService service = new RuntimeConfigService(file);
        RuntimeConfigService.Config defaults = service.load();
        assertTrue(Files.isRegularFile(file));
        assertEquals(600L, defaults.npcCheckpointTicks());
        assertEquals(8, defaults.storyMaxActiveEvents());
        assertEquals("balanced", defaults.storyTone());
        assertTrue(defaults.storyCategoryEnabled(WorldEventEngine.Category.CRIME));

        Files.writeString(file, """
                persistence.npc_checkpoint_ticks=900
                persistence.simulation_autosave_ticks=7200
                story.pulse_ticks=1600
                story.max_active_events=5
                story.max_new_events_per_pulse=1
                story.tone=dark
                story.disabled_categories=POLITICAL,DISASTER
                ai.decisions_per_pulse=14
                ai.max_pending=512
                social.max_interactions_per_pulse=20
                ai.max_observed_entities=48
                """);
        RuntimeConfigService.Config reloaded = service.reload();
        assertEquals(900L, reloaded.npcCheckpointTicks());
        assertEquals(5, reloaded.storyMaxActiveEvents());
        assertEquals("dark", reloaded.storyTone());
        assertEquals(14, reloaded.aiDecisionsPerPulse());
        assertFalse(reloaded.storyCategoryEnabled(WorldEventEngine.Category.POLITICAL));
        assertFalse(reloaded.storyCategoryEnabled(WorldEventEngine.Category.DISASTER));
        assertTrue(reloaded.storyCategoryEnabled(WorldEventEngine.Category.CRIME));
    }

    @Test
    void rejectsUnsafeBudgetsUnknownCategoriesAndInvalidToneWithoutReplacingCurrentSnapshot() throws Exception {
        Path file = temp.resolve("runtime.properties");
        RuntimeConfigService service = new RuntimeConfigService(file);
        RuntimeConfigService.Config baseline = service.load();

        Files.writeString(file, "story.max_active_events=999999\n");
        assertThrows(IllegalArgumentException.class, service::reload);
        assertEquals(baseline, service.current());

        Files.writeString(file, "story.disabled_categories=ALIEN_INVASION\n");
        assertThrows(IllegalArgumentException.class, service::reload);
        assertEquals(baseline, service.current());

        Files.writeString(file, "story.tone=edgelord\n");
        assertThrows(IllegalArgumentException.class, service::reload);
        assertEquals(baseline, service.current());
    }
}
