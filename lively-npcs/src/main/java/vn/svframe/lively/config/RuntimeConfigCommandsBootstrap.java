package vn.svframe.lively.config;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import vn.svframe.lively.api.LivelyApi;

import static net.minecraft.server.command.CommandManager.literal;

/** Hot-reload command surface for settings that are safe to change while a world session is live. */
public final class RuntimeConfigCommandsBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("lively")
                        .then(literal("reload")
                                .requires(source -> LivelyApi.permissions().has(source, "lively.admin.reload", 2))
                                .then(literal("config").executes(ctx -> reload(ctx.getSource())))
                                .then(literal("all").executes(ctx -> reload(ctx.getSource()))))));
    }

    private static int reload(ServerCommandSource source) {
        RuntimeConfigService service = LivelyApi.runtimeConfig();
        if (service == null) {
            source.sendError(Text.literal("Lively runtime config is not bound to an active server session."));
            return 0;
        }
        try {
            RuntimeConfigService.Config config = service.reload();
            source.sendFeedback(() -> Text.literal("Lively config reloaded: storyPulse=" + config.storyPulseTicks()
                    + ", maxActiveEvents=" + config.storyMaxActiveEvents()
                    + ", aiDecisions=" + config.aiDecisionsPerPulse()
                    + ", aiMaxPending=" + config.aiMaxPending()
                    + ", autosave=" + config.simulationAutosaveTicks()), false);
            return 1;
        } catch (RuntimeException error) {
            source.sendError(Text.literal("Lively config reload rejected: " + safe(error.getMessage())));
            return 0;
        }
    }

    private static String safe(String message) {
        if (message == null || message.isBlank()) return "invalid configuration";
        String clean = message.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() <= 180 ? clean : clean.substring(0, 180);
    }
}
