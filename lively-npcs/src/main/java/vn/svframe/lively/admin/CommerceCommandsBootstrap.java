package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.economy.PlayerCommerceService;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.society.SocietyApi;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Player shop commands used by the click-to-buy merchant menu. */
public final class CommerceCommandsBootstrap implements ModInitializer {
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("livelyshop")
                    .then(literal("open").then(argument("npc", StringArgumentType.word()).executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        PlayerCommerceService commerce = SocietyApi.commerce();
                        if (player == null || commerce == null || LivelyApi.npcs() == null) return 0;
                        UUID npc = resolveNpc(StringArgumentType.getString(context, "npc"));
                        if (npc == null) { player.sendMessage(Text.literal("Không tìm thấy NPC."), false); return 0; }
                        return commerce.present(player, npc) ? 1 : 0;
                    })))
                    .then(literal("buy")
                            .then(argument("business", StringArgumentType.word())
                                    .then(argument("item", StringArgumentType.word())
                                            .then(argument("quantity", IntegerArgumentType.integer(1, 2304)).executes(context -> {
                                                ServerPlayerEntity player = context.getSource().getPlayer();
                                                PlayerCommerceService commerce = SocietyApi.commerce();
                                                if (player == null || commerce == null) return 0;
                                                UUID business;
                                                try { business = UUID.fromString(StringArgumentType.getString(context, "business")); }
                                                catch (IllegalArgumentException ignored) { player.sendMessage(Text.literal("Business ID không hợp lệ."), false); return 0; }
                                                String item = StringArgumentType.getString(context, "item");
                                                int quantity = IntegerArgumentType.getInteger(context, "quantity");
                                                UUID playerId = player.getUuid();
                                                commerce.buy(player, business, item, quantity).whenComplete((result, error) -> {
                                                    var server = player.getServer();
                                                    if (server == null) return;
                                                    server.execute(() -> {
                                                        ServerPlayerEntity online = server.getPlayerManager().getPlayer(playerId);
                                                        if (online == null) return;
                                                        if (error != null) online.sendMessage(Text.literal("Giao dịch lỗi: " + error.getMessage()), false);
                                                        else online.sendMessage(Text.literal(result.message()), false);
                                                    });
                                                });
                                                return 1;
                                            })))))
                    .then(literal("providers").requires(source -> source.hasPermissionLevel(2)).executes(context -> {
                        context.getSource().sendFeedback(() -> Text.literal("Economy providers: " + SocietyApi.economies().providers()
                                + " routes=" + SocietyApi.economies().routes()), false);
                        return 1;
                    }))
                    .then(literal("route").requires(source -> source.hasPermissionLevel(2))
                            .then(argument("currency", StringArgumentType.word())
                                    .then(argument("provider", StringArgumentType.word()).executes(context -> {
                                        String currency = StringArgumentType.getString(context, "currency");
                                        String provider = StringArgumentType.getString(context, "provider");
                                        SocietyApi.economies().route(currency, provider);
                                        context.getSource().sendFeedback(() -> Text.literal("Runtime route set: " + currency + " -> " + provider
                                                + ". Persist it in config/livelynpcs/economy.properties if wanted after restart."), false);
                                        return 1;
                                    }))));
        });
    }

    private static UUID resolveNpc(String token) {
        try {
            UUID id = UUID.fromString(token);
            if (LivelyApi.npcs().get(id).isPresent()) return id;
        } catch (IllegalArgumentException ignored) { }
        return LivelyApi.npcs().snapshot().values().stream()
                .filter(definition -> definition.name().equalsIgnoreCase(token))
                .map(NpcDefinition::id).findFirst().orElse(null);
    }
}
