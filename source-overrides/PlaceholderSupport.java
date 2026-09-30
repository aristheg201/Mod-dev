package vn.svarcade.tcg.fabric;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.function.Function;

/** Optional Placeholder API provider. This class is only loaded when placeholder-api is installed. */
public final class PlaceholderSupport {
    private PlaceholderSupport() {}

    public static void register(TcgMod mod) {
        register("coins", p -> Long.toString(mod.placeholderCoins(p)));
        register("rating", p -> Integer.toString(mod.placeholderRating(p)));
        register("cards", p -> Integer.toString(mod.placeholderCards(p)));
        register("decks", p -> Integer.toString(mod.placeholderDecks(p)));
        register("duel_state", mod::placeholderDuelState);
        register("bot_difficulty", mod::placeholderBotDifficulty);
    }

    private static void register(String id, Function<ServerPlayerEntity,String> value) {
        Placeholders.register(Identifier.of("svarcade_tcg", id), (context, argument) -> {
            ServerPlayerEntity player = context.player();
            return player == null ? PlaceholderResult.invalid() : PlaceholderResult.value(value.apply(player));
        });
    }
}
