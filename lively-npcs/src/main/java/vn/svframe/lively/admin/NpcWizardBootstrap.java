package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import vn.svframe.lively.api.LivelyApi;

import java.util.UUID;

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
                                        .then(argument("id", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    try {
                                                        return NpcAdminWizard.show(ctx.getSource(), UUID.fromString(StringArgumentType.getString(ctx, "id")));
                                                    } catch (IllegalArgumentException error) {
                                                        ctx.getSource().sendError(net.minecraft.text.Text.literal("Invalid NPC UUID"));
                                                        return 0;
                                                    }
                                                }))))));
    }
}
