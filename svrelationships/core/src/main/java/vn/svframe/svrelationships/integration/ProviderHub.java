package vn.svframe.svrelationships.integration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ProviderHub {
    private final Map<String, EconomyProvider> economies = new ConcurrentHashMap<>();
    private volatile PermissionProvider permissionProvider;
    private volatile PokemonProvider pokemonProvider;

    public void registerEconomy(EconomyProvider provider) {
        economies.put(provider.id(), provider);
    }

    public Optional<EconomyProvider> economy(String id) {
        return Optional.ofNullable(economies.get(id));
    }

    public List<String> economyIds() {
        return economies.keySet().stream().sorted().toList();
    }

    public void setPermissionProvider(PermissionProvider provider) {
        this.permissionProvider = provider;
    }

    public Optional<PermissionProvider> permissionProvider() {
        return Optional.ofNullable(permissionProvider);
    }

    public void setPokemonProvider(PokemonProvider provider) {
        this.pokemonProvider = provider;
    }

    public Optional<PokemonProvider> pokemonProvider() {
        return Optional.ofNullable(pokemonProvider);
    }
}
