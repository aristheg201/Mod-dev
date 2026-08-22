package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import vn.svframe.lively.animation.AnimationResult;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcReference;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Small admin surface for exercising the server-side animation engine in a live server. */
public final class AnimationCommandsBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var animation = argument("animation", StringArgumentType.greedyString())
                    .executes(ctx -> animate(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "npc"),
                            StringArgumentType.getString(ctx, "animation")));
            var npc = argument("npc", StringArgumentType.string())
                    .suggests((ctx, builder) -> CommandSource.suggestMatching(suggestions(), builder))
                    .then(animation);
            LiteralArgumentBuilder<ServerCommandSource> animate = literal("animate")
                    .requires(source -> LivelyApi.permissions().has(source, "lively.admin.npc.animate", 2))
                    .then(npc);
            dispatcher.register(literal("lively").then(literal("npc").then(animate)));
        });
    }

    private static int animate(ServerCommandSource source, String reference, String animation) {
        if (LivelyApi.npcs() == null) {
            source.sendError(Text.literal("Lively NPC runtime is not available in this server session"));
            return 0;
        }
        NpcReference.Resolution resolution = NpcReference.resolve(LivelyApi.npcs(), reference);
        if (!resolution.found()) {
            source.sendError(Text.literal(resolution.error()));
            return 0;
        }
        if (LivelyApi.animations() == null) {
            source.sendError(Text.literal("Lively animation engine is not available in this server session"));
            return 0;
        }
        AnimationResult result = LivelyApi.animations().play(source.getServer(), resolution.id(), animation);
        if (!result.accepted()) {
            source.sendError(Text.literal("Animation rejected: " + result.detail()));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Animation " + result.animation() + " -> " + result.detail()), false);
        return 1;
    }

    private static List<String> suggestions() {
        return NpcReference.names(LivelyApi.npcs()).stream().map(AnimationCommandsBootstrap::quote).toList();
    }

    private static String quote(String value) {
        return value.indexOf(' ') >= 0 ? "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : value;
    }
}
