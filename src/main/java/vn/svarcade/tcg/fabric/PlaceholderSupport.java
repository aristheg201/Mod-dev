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
        for(String key:java.util.List.of("player","duel_state","duel_turn","duel_phase","lp","opponent_lp","hand_size","deck_size","graveyard_size","banished_size","selected_deck","collection_count","wins","losses","rank","rating","last_reward","coins","cards","decks","bot_difficulty"))
            register(key,p->mod.placeholderValue(p,key));

    }

    private static void register(String id, Function<ServerPlayerEntity,String> value) {
        for(String namespace:java.util.List.of("svarcade_tcg","cardworlds"))Placeholders.register(Identifier.of(namespace,id), (context, argument) -> {
            ServerPlayerEntity player = context.player();
            return player == null ? PlaceholderResult.invalid() : PlaceholderResult.value(value.apply(player));
        });
    }
}
