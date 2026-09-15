package vn.svframe.svrelationships.fabric.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PokemonReferenceResolver {
    private final RelationshipService relationships;

    public PokemonReferenceResolver(RelationshipService relationships) {
        this.relationships = relationships;
    }

    public Optional<UUID> resolve(ServerPlayerEntity player, String reference) {
        try {
            if (reference.startsWith("uuid:")) return Optional.of(UUID.fromString(reference.substring(5)));
            if (reference.startsWith("party:")) {
                int index = parseIndex(reference.substring(6));
                List<Pokemon> party = new ArrayList<>();
                Cobblemon.INSTANCE.getStorage().getParty(player).forEach(party::add);
                return index < party.size() ? Optional.of(party.get(index).getUuid()) : Optional.empty();
            }
            if (reference.startsWith("storage:")) {
                int index = parseIndex(reference.substring(8));
                List<Pokemon> stored = new ArrayList<>();
                Cobblemon.INSTANCE.getStorage().getPC(player).forEach(stored::add);
                return index < stored.size() ? Optional.of(stored.get(index).getUuid()) : Optional.empty();
            }
            if (reference.startsWith("partner:")) {
                int index = parseIndex(reference.substring(8));
                var partners = relationships.partners(player.getUuid());
                return index < partners.size() ? Optional.of(partners.get(index).key().pokemonId()) : Optional.empty();
            }
            return Optional.of(UUID.fromString(reference));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public List<Suggestion> suggestions(ServerPlayerEntity player) {
        List<Suggestion> result = new ArrayList<>();
        int partyIndex = 1;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            result.add(new Suggestion("party:" + partyIndex++, pokemon.getSpecies().getName()));
        }
        int partnerIndex = 1;
        for (var relationship : relationships.partners(player.getUuid())) {
            String label = "";
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                if (pokemon.getUuid().equals(relationship.key().pokemonId())) { label = pokemon.getSpecies().getName(); break; }
            }
            if (label.isBlank()) {
                for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) {
                    if (pokemon.getUuid().equals(relationship.key().pokemonId())) { label = pokemon.getSpecies().getName(); break; }
                }
            }
            result.add(new Suggestion("partner:" + partnerIndex++, label));
        }
        int storageIndex = 1;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) {
            result.add(new Suggestion("storage:" + storageIndex++, pokemon.getSpecies().getName()));
        }
        return List.copyOf(result);
    }

    private static int parseIndex(String raw) {
        int parsed = Integer.parseInt(raw);
        if (parsed < 1) throw new IllegalArgumentException("index");
        return parsed - 1;
    }

    public record Suggestion(String value, String label) {}
}
