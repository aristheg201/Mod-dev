package vn.svframe.svrelationships.fabric.family;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.family.InheritanceDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

public final class CobblemonFamilyService {
    private static final List<Stat> BREEDABLE_STATS = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);
    private final MinecraftServer server;

    public CobblemonFamilyService(MinecraftServer server) { this.server = server; }

    public Optional<Pokemon> findOwned(UUID ownerId, UUID pokemonId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerId);
        if (player == null) return Optional.empty();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) if (pokemon.getUuid().equals(pokemonId)) return Optional.of(pokemon);
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) if (pokemon.getUuid().equals(pokemonId)) return Optional.of(pokemon);
        return Optional.empty();
    }

    public Optional<Pokemon> createOffspring(UUID ownerId, List<UUID> parentIds, List<String> participantRoles,
                                              String speciesSource, InheritanceDefinition inheritance, long seed) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerId);
        if (player == null || parentIds.isEmpty()) return Optional.empty();
        List<Pokemon> parents = new ArrayList<>();
        for (UUID id : parentIds) {
            Optional<Pokemon> pokemon = findOwned(ownerId, id);
            if (pokemon.isEmpty()) return Optional.empty();
            parents.add(pokemon.get());
        }

        String species = resolveSpecies(speciesSource, parents, participantRoles);
        PokemonProperties properties = PokemonProperties.Companion.parse("species=" + species + " level=1");
        SplittableRandom random = new SplittableRandom(seed);
        Pokemon primary = parents.getFirst();
        if (random.nextDouble() < inheritance.natureChance()) {
            Pokemon parent = parents.get(random.nextInt(parents.size())); properties.setNature(parent.getNature().getName().toString());
        }
        if (random.nextDouble() < inheritance.abilityChance()) {
            Pokemon parent = parents.get(random.nextInt(parents.size())); properties.setAbility(parent.getAbility().getName());
        }
        if (inheritance.inheritMoves()) {
            Pokemon parent = parents.get(random.nextInt(parents.size())); List<String> moves = new ArrayList<>(); parent.getMoveSet().forEach(move -> moves.add(move.getName())); properties.setMoves(moves);
        }
        if ("parent".equalsIgnoreCase(inheritance.aspectMode())) {
            Pokemon parent = parents.get(random.nextInt(parents.size())); if (!parent.getAspects().isEmpty()) properties.setAspects(new HashSet<>(parent.getAspects()));
        }
        Pokemon offspring = properties.create();
        inheritIvs(offspring, parents, inheritance.guaranteedIvCount(), random);
        offspring.setOriginalTrainer(player.getUuid()); offspring.setLevel(1); offspring.heal();
        boolean stored = Cobblemon.INSTANCE.getStorage().getParty(player).add(offspring);
        return stored ? Optional.of(offspring) : Optional.empty();
    }

    private static String resolveSpecies(String source, List<Pokemon> parents, List<String> roles) {
        if (source.startsWith("fixed:")) {
            String species = source.substring("fixed:".length()).trim();
            if (species.isBlank()) throw new IllegalArgumentException("Empty fixed offspring species");
            return species;
        }
        String role = source.substring("participant:".length()).trim();
        int index = "first".equals(role) ? 0 : roles.indexOf(role);
        if (index < 0 || index >= parents.size()) throw new IllegalArgumentException("Unknown offspring species participant role: " + role);
        return parents.get(index).getSpecies().getResourceIdentifier().toString();
    }

    private static void inheritIvs(Pokemon offspring, List<Pokemon> parents, int guaranteedIvCount, SplittableRandom random) {
        int count = Math.min(Math.max(0, guaranteedIvCount), BREEDABLE_STATS.size()); if (count == 0) return;
        List<Stat> stats = new ArrayList<>(BREEDABLE_STATS);
        for (int i = stats.size() - 1; i > 0; i--) { int swap = random.nextInt(i + 1); Stat current = stats.get(i); stats.set(i, stats.get(swap)); stats.set(swap, current); }
        for (int i = 0; i < count; i++) { Stat stat = stats.get(i); Pokemon parent = parents.get(random.nextInt(parents.size())); Integer value = parent.getIvs().get(stat); if (value != null) offspring.setIV(stat, value); }
    }
}
