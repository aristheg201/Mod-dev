package vn.svframe.lively.integration.cobblemon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.IdentifierArgumentType;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var role = argument("role", StringArgumentType.word())
                    .executes(ctx -> create(
                            ctx.getSource(),
                            IdentifierArgumentType.getIdentifier(ctx, "npcClass"), null,
                            IntegerArgumentType.getInteger(ctx, "level"),
                            IntegerArgumentType.getInteger(ctx, "skill"),
                            StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "role")));
            role.then(argument("preset", IdentifierArgumentType.identifier())
                    .executes(ctx -> create(
                            ctx.getSource(),
                            IdentifierArgumentType.getIdentifier(ctx, "npcClass"),
                            IdentifierArgumentType.getIdentifier(ctx, "preset"),
                            IntegerArgumentType.getInteger(ctx, "level"),
                            IntegerArgumentType.getInteger(ctx, "skill"),
                            StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "role"))));
            var name = argument("name", StringArgumentType.string()).then(role);
            var skill = argument("skill", IntegerArgumentType.integer(1, 5)).then(name);
            var level = argument("level", IntegerArgumentType.integer(1, 1000)).then(skill);
            var npcClass = argument("npcClass", IdentifierArgumentType.identifier()).then(level);

            LiteralArgumentBuilder<ServerCommandSource> trainer = literal("trainer")
                    .requires(source -> LivelyApi.permissions().has(source, "lively.admin.npc", 2))
                    .then(npcClass);
            dispatcher.register(literal("lively")
                    .then(literal("npc")
                            .then(literal("create").then(trainer))));
        });
    }

    private static int create(ServerCommandSource source, Identifier id, Identifier preset,
                              int level, int skill, String name, String role) {
        if (LivelyApi.npcs() == null) {
            source.sendError(Text.literal("Lively NPC runtime is not active."));
            return 0;
        }
        try {
            ServerPlayerEntity player = source.getPlayer();
            float yaw = player == null ? 0F : player.getYaw();
            float pitch = player == null ? 0F : player.getPitch();
            String body = "npc:" + id
                    + (preset == null ? "" : ";preset=" + preset)
                    + ";level=" + level + ";skill=" + skill + ";native_interaction=true";
            NpcDefinition definition = LivelyApi.npcs().create(
                    name, role, NpcDefinition.BodyType.EXTERNAL, body, "",
                    source.getWorld().getRegistryKey().getValue().toString(), source.getPosition(), yaw, pitch);
            if (!LivelyApi.npcs().spawn(source.getServer(), definition.id())) {
                source.sendError(Text.literal("Trainer definition created but Cobblemon rejected the body spawn."));
                return 0;
            }
            source.sendFeedback(() -> Text.literal("Created Lively Cobblemon trainer " + name
                    + " id=" + definition.id() + " class=" + id
                    + (preset == null ? "" : " preset=" + preset)
                    + " level=" + level + " skill=" + skill), false);
            return 1;
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            source.sendError(Text.literal("Trainer create failed: " + message));
            return 0;
        }
    }
}
