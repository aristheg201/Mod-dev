package io.github.aristheg201.cobblemonworld.progression;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import net.minecraft.server.level.ServerPlayer;

public final class LevelCapService {
    private LevelCapService() {}

    public static int getCap(ServerPlayer player) {
        return ProgressionStore.INSTANCE.getOrCreate(player.getUUID()).levelCap;
    }

    public static int setCap(ServerPlayer player, int requestedCap) {
        int cap = Math.max(1, Math.min(CWorldConfig.INSTANCE.maxLevelCap, requestedCap));
        ProgressionStore.INSTANCE.getOrCreate(player.getUUID()).levelCap = cap;
        ProgressionStore.INSTANCE.save();
        return cap;
    }

    public static boolean isOverCap(ServerPlayer player, Pokemon pokemon) {
        return pokemon != null && pokemon.getLevel() > getCap(player);
    }

    public static Pokemon firstOverCapPartyPokemon(ServerPlayer player) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int cap = getCap(player);
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null && pokemon.getLevel() > cap) return pokemon;
        }
        return null;
    }
}
