package vn.svframe.lively.config;

import vn.svframe.lively.event.WorldEventEngine;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Small, dependency-free production runtime configuration.
 *
 * <p>The service owns only scheduling/budget/tone controls. World/NPC state stays world-scoped and is never stored in
 * config. Reload atomically replaces an immutable snapshot so running services observe new limits without retaining a
 * mutable Properties instance.</p>
 */
public final class RuntimeConfigService {
    public record Config(
            long npcCheckpointTicks,
            long simulationAutosaveTicks,
            long storyPulseTicks,
            int storyMaxActiveEvents,
            int storyMaxNewEventsPerPulse,
            String storyTone,
            Set<WorldEventEngine.Category> storyEnabledCategories,
            int aiDecisionsPerPulse,
            int aiMaxPending,
            int socialInteractionsPerPulse,
            int maxObservedEntities
    ) {
        public Config {
            storyTone = normalizeTone(storyTone);
            storyEnabledCategories = Set.copyOf(storyEnabledCategories);
        }

        public boolean storyCategoryEnabled(WorldEventEngine.Category category) {
            return storyEnabledCategories.contains(category);
        }
    }

    private static final Set<String> STORY_TONES = Set.of("balanced", "peaceful", "adventure", "dramatic", "dark");
    private static final Config DEFAULTS = new Config(
            600L,
            6000L,
            1200L,
            8,
            2,
            "balanced",
            EnumSet.allOf(WorldEventEngine.Category.class),
            10,
            1024,
            32,
            64);

    private final Path file;
    private volatile Config current = DEFAULTS;

    public RuntimeConfigService(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public static Config defaults() { return DEFAULTS; }

    public synchronized Config load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.isRegularFile(file)) writeDefaults();
            Properties p = new Properties();
            try (Reader reader = Files.newBufferedReader(file)) { p.load(reader); }
            Config parsed = parse(p);
            current = parsed;
            return parsed;
        } catch (IOException error) {
            throw new IllegalStateException("failed to load Lively runtime config " + file, error);
        }
    }

    public Config reload() { return load(); }
    public Config current() { return current; }
    public Path file() { return file; }

    private Config parse(Properties p) {
        long checkpoint = longValue(p, "persistence.npc_checkpoint_ticks", DEFAULTS.npcCheckpointTicks(), 100L, 72_000L);
        long autosave = longValue(p, "persistence.simulation_autosave_ticks", DEFAULTS.simulationAutosaveTicks(), 200L, 144_000L);
        long storyPulse = longValue(p, "story.pulse_ticks", DEFAULTS.storyPulseTicks(), 100L, 72_000L);
        int maxActive = intValue(p, "story.max_active_events", DEFAULTS.storyMaxActiveEvents(), 1, 128);
        int maxNew = intValue(p, "story.max_new_events_per_pulse", DEFAULTS.storyMaxNewEventsPerPulse(), 0, 16);
        String tone = normalizeTone(p.getProperty("story.tone", DEFAULTS.storyTone()));
        int decisions = intValue(p, "ai.decisions_per_pulse", DEFAULTS.aiDecisionsPerPulse(), 1, 128);
        int pending = intValue(p, "ai.max_pending", DEFAULTS.aiMaxPending(), 32, 8192);
        int social = intValue(p, "social.max_interactions_per_pulse", DEFAULTS.socialInteractionsPerPulse(), 1, 256);
        int observed = intValue(p, "ai.max_observed_entities", DEFAULTS.maxObservedEntities(), 8, 256);

        EnumSet<WorldEventEngine.Category> enabled = EnumSet.allOf(WorldEventEngine.Category.class);
        String disabled = p.getProperty("story.disabled_categories", "").trim();
        if (!disabled.isBlank()) {
            for (String raw : disabled.split(",")) {
                String value = raw.trim();
                if (value.isEmpty()) continue;
                try { enabled.remove(WorldEventEngine.Category.valueOf(value.toUpperCase(Locale.ROOT))); }
                catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException("unknown story category in story.disabled_categories: " + value);
                }
            }
        }

        return new Config(checkpoint, autosave, storyPulse, maxActive, maxNew, tone, enabled,
                decisions, pending, social, observed);
    }

    private void writeDefaults() throws IOException {
        Properties p = new Properties();
        p.setProperty("persistence.npc_checkpoint_ticks", Long.toString(DEFAULTS.npcCheckpointTicks()));
        p.setProperty("persistence.simulation_autosave_ticks", Long.toString(DEFAULTS.simulationAutosaveTicks()));
        p.setProperty("story.pulse_ticks", Long.toString(DEFAULTS.storyPulseTicks()));
        p.setProperty("story.max_active_events", Integer.toString(DEFAULTS.storyMaxActiveEvents()));
        p.setProperty("story.max_new_events_per_pulse", Integer.toString(DEFAULTS.storyMaxNewEventsPerPulse()));
        p.setProperty("story.tone", DEFAULTS.storyTone());
        p.setProperty("story.disabled_categories", "");
        p.setProperty("ai.decisions_per_pulse", Integer.toString(DEFAULTS.aiDecisionsPerPulse()));
        p.setProperty("ai.max_pending", Integer.toString(DEFAULTS.aiMaxPending()));
        p.setProperty("social.max_interactions_per_pulse", Integer.toString(DEFAULTS.socialInteractionsPerPulse()));
        p.setProperty("ai.max_observed_entities", Integer.toString(DEFAULTS.maxObservedEntities()));

        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp)) {
            p.store(writer, "Lively NPCs production runtime budgets. Times are Minecraft ticks (20 ticks = 1 second).");
        }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static String normalizeTone(String raw) {
        String tone = raw == null ? "balanced" : raw.trim().toLowerCase(Locale.ROOT);
        if (!STORY_TONES.contains(tone)) {
            throw new IllegalArgumentException("story.tone must be one of " + STORY_TONES + ", got: " + raw);
        }
        return tone;
    }

    private static int intValue(Properties p, String key, int fallback, int min, int max) {
        String raw = p.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid integer for " + key + ": " + raw, error);
        }
    }

    private static long longValue(Properties p, String key, long fallback, long min, long max) {
        String raw = p.getProperty(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            long value = Long.parseLong(raw.trim());
            if (value < min || value > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid long for " + key + ": " + raw, error);
        }
    }
}
