package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcReference;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Adds the click-driven wizard to the existing /lively npc command tree. */
public final class NpcWizardBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("lively")
                        .then(literal("npc")
                                .requires(source -> LivelyApi.permissions().has(source, "lively.admin.npc", 2))
                                .then(literal("wizard")
                                        .executes(ctx -> NpcAdminWizard.show(ctx.getSource()))
                                        .then(argument("npc", StringArgumentType.string())
                                                .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                                        NpcReference.names(LivelyApi.npcs()).stream().map(NpcWizardBootstrap::quote).toList(), builder))
                                                .executes(ctx -> {
                                                    if (LivelyApi.npcs() == null) {
                                                        ctx.getSource().sendError(Text.literal("Lively NPC runtime is not ready"));
                                                        return 0;
                                                    }
                                                    NpcReference.Resolution resolution = NpcReference.resolve(
                                                            LivelyApi.npcs(), StringArgumentType.getString(ctx, "npc"));
                                                    if (!resolution.found()) {
                                                        ctx.getSource().sendError(Text.literal(resolution.error()));
                                                        return 0;
                                                    }
                                                    return NpcAdminWizard.show(ctx.getSource(), resolution.id());
                                                }))))));
    }

    private static String quote(String value) {
        return value.indexOf(' ') >= 0 ? "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : value;
    }
}
