package vn.svframe.lively.integration.cobblemon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcDefinition;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Admin command for command-created native Cobblemon trainer bodies. */
public final class CobblemonTrainerCommandsBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("lively")
                        .then(literal("npc")
                                .then(literal("create")
                                        .then(literal("trainer")
                                                .requires(source -> LivelyApi.permissions().has(source, "lively.admin.npc", 2))
                                                .then(argument("npcClass", StringArgumentType.word())
                                                        .then(argument("level", IntegerArgumentType.integer(1, 1000))
                                                                .then(argument("skill", IntegerArgumentType.integer(1, 5))
                                                                        .then(argument("name", StringArgumentType.string())
                                                                                .then(argument("role", StringArgumentType.word())
                                                                                        .executes(ctx -> create(
                                                                                                ctx.getSource(),
                                                                                                StringArgumentType.getString(ctx, "npcClass"),
                                                                                                IntegerArgumentType.getInteger(ctx, "level"),
                                                                                                IntegerArgumentType.getInteger(ctx, "skill"),
                                                                                                StringArgumentType.getString(ctx, "name"),
                                                                                                StringArgumentType.getString(ctx, "role"))))))))))))));
    }

    private static int create(ServerCommandSource source, String npcClass, int level, int skill, String name, String role) {
        Identifier id = Identifier.tryParse(npcClass);
        if (id == null) {
            source.sendError(Text.literal("Invalid Cobblemon NPC class identifier: " + npcClass));
            return 0;
        }
        if (LivelyApi.npcs() == null) {
            source.sendError(Text.literal("Lively NPC runtime is not active."));
            return 0;
        }
        try {
            ServerPlayerEntity player = source.getPlayer();
            float yaw = player == null ? 0F : player.getYaw();
            float pitch = player == null ? 0F : player.getPitch();
            String body = "npc:" + id + ";level=" + level + ";skill=" + skill + ";native_interaction=true";
            NpcDefinition definition = LivelyApi.npcs().create(
                    name, role, NpcDefinition.BodyType.EXTERNAL, body, "",
                    source.getWorld().getRegistryKey().getValue().toString(), source.getPosition(), yaw, pitch);
            if (!LivelyApi.npcs().spawn(source.getServer(), definition.id())) {
                source.sendError(Text.literal("Trainer definition created but Cobblemon rejected the body spawn."));
                return 0;
            }
            source.sendFeedback(() -> Text.literal("Created Lively Cobblemon trainer " + name
                    + " id=" + definition.id() + " class=" + id + " level=" + level + " skill=" + skill), false);
            return 1;
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            source.sendError(Text.literal("Trainer create failed: " + message));
            return 0;
        }
    }
}
