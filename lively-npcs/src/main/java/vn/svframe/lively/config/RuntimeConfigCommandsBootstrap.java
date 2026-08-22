package vn.svframe.lively.config;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import vn.svframe.lively.api.LivelyApi;

import java.time.Instant;

import static net.minecraft.server.command.CommandManager.literal;

/** Hot-reload command surface for settings and runtime indexes that are safe to refresh while a world is live. */
public final class RuntimeConfigCommandsBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> reload = literal("reload")
                    .requires(source -> LivelyApi.permissions().has(source, "lively.admin.reload", 2));
            reload.then(literal("config").executes(ctx -> reloadConfig(ctx.getSource())));
            reload.then(literal("npcs").executes(ctx -> reloadNpcs(ctx.getSource())));
            reload.then(literal("quests").executes(ctx -> reloadQuests(ctx.getSource())));
            reload.then(literal("dialogue").executes(ctx -> reloadDialogue(ctx.getSource())));
            reload.then(literal("locations").executes(ctx -> reloadLocations(ctx.getSource())));
            reload.then(literal("all").executes(ctx -> reloadAll(ctx.getSource())));
            dispatcher.register(literal("lively").then(reload));
        });
    }

    private static int reloadConfig(ServerCommandSource source) {
        RuntimeConfigService service = LivelyApi.runtimeConfig();
        if (service == null) return error(source, "Lively runtime config is not bound to an active server session.");
        try {
            RuntimeConfigService.Config config = service.reload();
            source.sendFeedback(() -> Text.literal("Lively config reloaded: storyTone=" + config.storyTone()
                    + ", storyPulse=" + config.storyPulseTicks()
                    + ", maxActiveEvents=" + config.storyMaxActiveEvents()
                    + ", aiDecisions=" + config.aiDecisionsPerPulse()
                    + ", aiMaxPending=" + config.aiMaxPending()
                    + ", autosave=" + config.simulationAutosaveTicks()), false);
            return 1;
        } catch (RuntimeException error) {
            return error(source, "Lively config reload rejected: " + safe(error.getMessage()));
        }
    }

    /** Reconciles missing physical bodies from the authoritative in-memory definitions; it never replaces live state from disk. */
    private static int reloadNpcs(ServerCommandSource source) {
        if (LivelyApi.npcs() == null) return error(source, "Lively NPC runtime is not active.");
        try {
            LivelyApi.npcs().restoreSpawned(source.getServer());
            LivelyApi.npcs().checkpoint();
            int definitions = LivelyApi.npcs().snapshot().size();
            source.sendFeedback(() -> Text.literal("Lively NPC runtime refreshed: definitions=" + definitions
                    + ". Live cognitive state was retained."), false);
            return 1;
        } catch (RuntimeException error) {
            return error(source, "NPC runtime refresh failed: " + safe(error.getMessage()));
        }
    }

    /** Keeps authoritative quest state, expires stale offers/tasks and refreshes player-facing navigation on next lifecycle/join signal. */
    private static int reloadQuests(ServerCommandSource source) {
        try {
            int expired = LivelyApi.quests().expire(Instant.now());
            int total = LivelyApi.quests().snapshot().quests().size();
            source.sendFeedback(() -> Text.literal("Lively quest runtime refreshed: total=" + total + ", expired=" + expired
                    + ". Active quest progress was retained."), false);
            return 1;
        } catch (RuntimeException error) {
            return error(source, "Quest runtime refresh failed: " + safe(error.getMessage()));
        }
    }

    /** Rebinds the local dialogue runtime and deliberately closes existing sessions so stale nonces/context cannot survive reload. */
    private static int reloadDialogue(ServerCommandSource source) {
        if (LivelyApi.dialogues() == null) return error(source, "Lively dialogue runtime is not active.");
        LivelyApi.dialogues().bindSession();
        source.sendFeedback(() -> Text.literal("Lively dialogue runtime reloaded; active dialogue sessions were closed safely."), false);
        return 1;
    }

    /** Requeues semantic capability scans. Registry state is kept; scans update discovered capabilities/points in place. */
    private static int reloadLocations(ServerCommandSource source) {
        if (LivelyApi.structureScanner() == null) return error(source, "Lively structure scanner is not active.");
        int requested = 0;
        int total = 0;
        for (var structure : LivelyApi.structures().snapshot().structures().values()) {
            total++;
            if (LivelyApi.structureScanner().request(structure.id())) requested++;
        }
        int queued = requested;
        int count = total;
        source.sendFeedback(() -> Text.literal("Lively locations refreshed: scanRequested=" + queued + "/" + count
                + ". Existing semantic state was retained."), false);
        return 1;
    }

    private static int reloadAll(ServerCommandSource source) {
        int failures = 0;
        if (reloadConfig(source) == 0) failures++;
        if (reloadNpcs(source) == 0) failures++;
        if (reloadQuests(source) == 0) failures++;
        if (reloadDialogue(source) == 0) failures++;
        if (reloadLocations(source) == 0) failures++;
        if (failures > 0) return error(source, "Lively reload all completed with " + failures + " failed subsystem(s).");
        source.sendFeedback(() -> Text.literal("Lively reload all completed without replacing live world state."), false);
        return 1;
    }

    private static int error(ServerCommandSource source, String message) {
        source.sendError(Text.literal(message));
        return 0;
    }

    private static String safe(String message) {
        if (message == null || message.isBlank()) return "invalid configuration";
        String clean = message.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() <= 180 ? clean : clean.substring(0, 180);
    }
}
