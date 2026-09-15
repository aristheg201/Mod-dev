package vn.svframe.svrelationships.fabric.relationship;

import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.persistence.RelationshipRepository;
import vn.svframe.svrelationships.gameplay.PartnerCapacityResolver;
import vn.svframe.svrelationships.gameplay.ProgressionEngine;
import vn.svframe.svrelationships.gameplay.RouteEngine;
import vn.svframe.svrelationships.integration.ProviderHub;
import vn.svframe.svrelationships.relationship.RelationshipKey;
import vn.svframe.svrelationships.relationship.RelationshipState;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RelationshipService {
    private final RelationshipRepository repository;
    private final GameplayDefinitionService definitions;
    private final ProviderHub providers;
    private final PartnerCapacityResolver capacityResolver = new PartnerCapacityResolver();

    public RelationshipService(RelationshipRepository repository, GameplayDefinitionService definitions, ProviderHub providers) {
        this.repository = repository;
        this.definitions = definitions;
        this.providers = providers;
    }

    public RelationshipState state(UUID playerId, UUID pokemonId) { return repository.getOrCreate(new RelationshipKey(playerId, pokemonId)); }
    public Optional<RelationshipState> existing(UUID playerId, UUID pokemonId) { return repository.get(new RelationshipKey(playerId, pokemonId)); }
    public List<RelationshipState> states(UUID playerId) { return repository.byPlayer(playerId); }
    public List<RelationshipState> partners(UUID playerId) { return repository.partners(playerId); }
    public long progression(UUID playerId, UUID pokemonId, String trackId) { return state(playerId, pokemonId).progression(trackId); }

    public long setProgression(UUID playerId, UUID pokemonId, String trackId, long value) {
        long result = new ProgressionEngine(definitions.snapshot().progressionTracks()).set(state(playerId, pokemonId), trackId, value);
        repository.markDirty(); return result;
    }
    public long addProgression(UUID playerId, UUID pokemonId, String trackId, long delta) {
        long result = new ProgressionEngine(definitions.snapshot().progressionTracks()).add(state(playerId, pokemonId), trackId, delta);
        repository.markDirty(); return result;
    }

    public boolean applyProgressionOnce(UUID playerId, UUID pokemonId, String markerId, Map<String, Long> deltas) {
        RelationshipState state = state(playerId, pokemonId);
        synchronized (state) {
            if (state.flag(markerId) != null) return false;
            ProgressionEngine engine = new ProgressionEngine(definitions.snapshot().progressionTracks());
            for (var delta : deltas.entrySet()) engine.add(state, delta.getKey(), delta.getValue());
            state.setFlag(markerId, "true");
            repository.markDirty();
            return true;
        }
    }

    public Optional<String> rank(UUID playerId, UUID pokemonId, String trackId) {
        var engine = new ProgressionEngine(definitions.snapshot().progressionTracks());
        return engine.rank(trackId, progression(playerId, pokemonId, trackId)).map(rank -> rank.id());
    }
    public String currentRoute(UUID playerId, UUID pokemonId, String routeId) {
        String result = new RouteEngine(definitions.snapshot().routes()).currentState(state(playerId, pokemonId), routeId);
        repository.markDirty(); return result;
    }
    public String transition(UUID playerId, UUID pokemonId, String routeId, String target) {
        String result = new RouteEngine(definitions.snapshot().routes()).transition(state(playerId, pokemonId), routeId, target);
        repository.markDirty(); return result;
    }
    public void forceRoute(UUID playerId, UUID pokemonId, String routeId, String stateId) {
        new RouteEngine(definitions.snapshot().routes()).force(state(playerId, pokemonId), routeId, stateId); repository.markDirty();
    }
    public int capacity(UUID playerId) {
        return capacityResolver.resolve(definitions.snapshot().partnerCapacity(), permission ->
                providers.permissionProvider().map(provider -> provider.hasPermission(playerId, permission)).orElse(false));
    }
    public boolean canAddPartner(UUID playerId) { return partners(playerId).size() < capacity(playerId); }
    public boolean setPartner(UUID playerId, UUID pokemonId, boolean partner, boolean force) {
        RelationshipState state = state(playerId, pokemonId);
        if (partner && !state.partner() && !force && !canAddPartner(playerId)) return false;
        state.setPartner(partner, System.currentTimeMillis()); repository.markDirty(); return true;
    }
    public void assignHousehold(UUID playerId, UUID pokemonId, UUID householdId) {
        state(playerId, pokemonId).setHouseholdId(householdId); repository.markDirty();
    }
    public void setPersonality(UUID playerId, UUID pokemonId, String personalityId) {
        if (personalityId != null && !personalityId.isBlank() && !definitions.snapshot().personalities().containsKey(personalityId)) {
            throw new IllegalArgumentException("Unknown personality: " + personalityId);
        }
        state(playerId, pokemonId).setPersonalityId(personalityId); repository.markDirty();
    }
    public void setCooldown(UUID playerId, UUID pokemonId, String id, long untilMillis) {
        state(playerId, pokemonId).setCooldownUntil(id, untilMillis); repository.markDirty();
    }
    public long cooldown(UUID playerId, UUID pokemonId, String id) { return state(playerId, pokemonId).cooldownUntil(id); }
    public void touch() { repository.markDirty(); }
    public List<RelationshipState> ordered(UUID playerId) {
        return states(playerId).stream().sorted(Comparator.comparing(s -> s.key().pokemonId().toString())).toList();
    }
}
