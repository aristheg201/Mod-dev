package vn.svframe.svrelationships.fabric.family;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.family.InheritanceDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

public final class CobblemonFamilyService {
    private final MinecraftServer server;

    public CobblemonFamilyService(MinecraftServer server) { this.server = server; }

    public Optional<Pokemon> findOwned(UUID ownerId, UUID pokemonId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerId);
        if (player == null) return Optional.empty();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) if (pokemon.getUuid().equals(pokemonId)) return Optional.of(pokemon);
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) if (pokemon.getUuid().equals(pokemonId)) return Optional.of(pokemon);
        return Optional.empty();
    }

    public Optional<Pokemon> createOffspring(UUID ownerId, List<UUID> parentIds, InheritanceDefinition inheritance, long seed) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerId);
        if (player == null || parentIds.isEmpty()) return Optional.empty();
        List<Pokemon> parents = new ArrayList<>();
        for (UUID id : parentIds) {
            Optional<Pokemon> pokemon = findOwned(ownerId, id);
            if (pokemon.isEmpty()) return Optional.empty();
            parents.add(pokemon.get());
        }
        Pokemon primary = parents.getFirst();
        String species = primary.getSpecies().getResourceIdentifier().getPath();
        PokemonProperties properties = PokemonProperties.Companion.parse("species=" + species + " level=1");
        SplittableRandom random = new SplittableRandom(seed);
        if (random.nextDouble() < inheritance.natureChance()) properties.setNature(primary.getNature().getName().toString());
        if (random.nextDouble() < inheritance.abilityChance()) properties.setAbility(primary.getAbility().getName());
        if (inheritance.inheritMoves()) {
            List<String> moves = primary.getMoveSet().getMoves().stream().map(move -> move.getName()).toList();
            properties.setMoves(moves);
        }
        if ("parent".equalsIgnoreCase(inheritance.aspectMode()) && !primary.getAspects().isEmpty()) {
            properties.setAspects(new java.util.HashSet<>(primary.getAspects()));
        }
        Pokemon offspring = properties.create();
        offspring.setOriginalTrainer(player.getUuid());
        offspring.setLevel(1);
        offspring.heal();
        boolean stored = Cobblemon.INSTANCE.getStorage().getParty(player).add(offspring);
        return stored ? Optional.of(offspring) : Optional.empty();
    }
}
