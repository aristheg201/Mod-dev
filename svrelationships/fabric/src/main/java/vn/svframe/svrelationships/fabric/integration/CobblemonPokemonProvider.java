package vn.svframe.svrelationships.fabric.integration;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.integration.PokemonProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CobblemonPokemonProvider implements PokemonProvider {
    private final MinecraftServer server;

    public CobblemonPokemonProvider(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String id() {
        return "cobblemon";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<PokemonSnapshot> ownedPokemon(UUID playerId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return List.of();
        }
        List<PokemonSnapshot> result = new ArrayList<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            result.add(snapshot(pokemon));
        }
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) {
            result.add(snapshot(pokemon));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<PokemonSnapshot> findOwned(UUID playerId, UUID pokemonId) {
        return ownedPokemon(playerId).stream().filter(pokemon -> pokemon.pokemonId().equals(pokemonId)).findFirst();
    }

    private static PokemonSnapshot snapshot(Pokemon pokemon) {
        String species = pokemon.getSpecies().getResourceIdentifier().toString();
        String form = pokemon.getForm() == null ? "" : pokemon.getForm().getName();
        Set<String> aspects = Set.copyOf(pokemon.getAspects());
        Optional<String> gender = Optional.ofNullable(pokemon.getGender()).map(Object::toString);
        return new PokemonSnapshot(pokemon.getUuid(), species, form, aspects, gender, pokemon.getLevel());
    }
}
