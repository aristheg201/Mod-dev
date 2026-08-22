package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import vn.svframe.lively.animation.AnimationResult;
import vn.svframe.lively.api.LivelyApi;

import java.util.UUID;

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
                            StringArgumentType.getString(ctx, "id"),
                            StringArgumentType.getString(ctx, "animation")));
            var id = argument("id", StringArgumentType.word()).then(animation);
            LiteralArgumentBuilder<ServerCommandSource> animate = literal("animate")
                    .requires(source -> LivelyApi.permissions().has(source, "lively.admin.npc.animate", 2))
                    .then(id);
            dispatcher.register(literal("lively").then(literal("npc").then(animate)));
        });
    }

    private static int animate(ServerCommandSource source, String rawId, String animation) {
        UUID npcId;
        try {
            npcId = UUID.fromString(rawId);
        } catch (IllegalArgumentException error) {
            source.sendError(Text.literal("Invalid NPC UUID: " + rawId));
            return 0;
        }
        if (LivelyApi.animations() == null) {
            source.sendError(Text.literal("Lively animation engine is not available in this server session"));
            return 0;
        }
        AnimationResult result = LivelyApi.animations().play(source.getServer(), npcId, animation);
        if (!result.accepted()) {
            source.sendError(Text.literal("Animation rejected: " + result.detail()));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Animation " + result.animation() + " -> " + result.detail()), false);
        return 1;
    }
}
