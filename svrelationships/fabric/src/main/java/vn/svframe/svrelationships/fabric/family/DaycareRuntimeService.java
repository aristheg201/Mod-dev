package vn.svframe.svrelationships.fabric.family;

import net.minecraft.server.MinecraftServer;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.persistence.DaycareRepository;
import vn.svframe.svrelationships.fabric.persistence.LineageRepository;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.family.DaycareEngine;
import vn.svframe.svrelationships.family.DaycareSession;
import vn.svframe.svrelationships.family.LineageRecord;
import vn.svframe.svrelationships.integration.EconomyProvider;
import vn.svframe.svrelationships.integration.ProviderHub;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;

public final class DaycareRuntimeService {
    private static final long DELIVERY_RETRY_MILLIS = 60_000L;

    private final MinecraftServer server;
    private final ConfigService config;
    private final GameplayDefinitionService definitions;
    private final DaycareRepository repository;
    private final LineageRepository lineage;
    private final RelationshipService relationships;
    private final ProviderHub providers;
    private final CobblemonFamilyService family;
    private final PriorityQueue<Deadline> deadlines = new PriorityQueue<>(Comparator.comparingLong(Deadline::dueAt));

    public DaycareRuntimeService(MinecraftServer server, ConfigService config, GameplayDefinitionService definitions,
                                 DaycareRepository repository, LineageRepository lineage, RelationshipService relationships,
                                 ProviderHub providers) {
        this.server = server;
        this.config = config;
        this.definitions = definitions;
        this.repository = repository;
        this.lineage = lineage;
        this.relationships = relationships;
        this.providers = providers;
        this.family = new CobblemonFamilyService(server);
        repository.active().forEach(session -> deadlines.add(new Deadline(session.completeAtMillis(), session.sessionId())));
    }

    public StartResult start(UUID ownerId, String definitionId, List<UUID> participants, long nowMillis) {
        var definition = definitions.snapshot().daycareDefinitions().get(definitionId);
        if (definition == null) return StartResult.UNKNOWN_DEFINITION;
        if (repository.byOwner(ownerId).stream().anyMatch(s -> "ACTIVE".equals(s.status()))) return StartResult.ALREADY_ACTIVE;
        for (UUID participant : participants) if (family.findOwned(ownerId, participant).isEmpty()) return StartResult.NOT_OWNED;
        if ("partner_family".equals(definition.mode())) {
            if (participants.isEmpty() || !relationships.state(ownerId, participants.getFirst()).partner()) return StartResult.NOT_PARTNER;
        }
        if (definition.cost() > 0 && !charge(ownerId, definition.economyProvider(), definition.currency(), definition.cost())) return StartResult.COST_FAILED;
        DaycareSession session;
        try {
            session = new DaycareEngine(definitions.snapshot().daycareDefinitions()).start(ownerId, definitionId, participants, nowMillis);
        } catch (RuntimeException exception) {
            return StartResult.INVALID_PARTICIPANTS;
        }
        repository.put(session);
        deadlines.add(new Deadline(session.completeAtMillis(), session.sessionId()));
        return StartResult.STARTED;
    }

    private boolean charge(UUID ownerId, String requestedProvider, String currency, long amount) {
        EconomyProvider provider = null;
        if (requestedProvider != null && !requestedProvider.isBlank()) provider = providers.economy(requestedProvider).orElse(null);
        if (provider == null) {
            for (String id : config.snapshot().economyPriority()) {
                var candidate = providers.economy(id);
                if (candidate.isPresent() && candidate.get().available()) {
                    provider = candidate.get();
                    break;
                }
            }
        }
        return provider != null && provider.withdraw(ownerId, currency == null || currency.isBlank() ? "default" : currency, BigDecimal.valueOf(amount));
    }

    public void tick(long nowMillis) {
        while (!deadlines.isEmpty() && deadlines.peek().dueAt() <= nowMillis) {
            Deadline deadline = deadlines.poll();
            repository.get(deadline.sessionId())
                    .filter(session -> session.due(nowMillis))
                    .ifPresent(session -> complete(session, nowMillis));
        }
    }

    public boolean completeNow(UUID sessionId, long nowMillis) {
        Optional<DaycareSession> session = repository.get(sessionId);
        return session.isPresent() && complete(session.get(), nowMillis);
    }

    private boolean complete(DaycareSession session, long nowMillis) {
        if (!"ACTIVE".equals(session.status())) return false;
        var definition = definitions.snapshot().daycareDefinitions().get(session.definitionId());
        if (definition == null) {
            repository.put(session.withStatus("INVALID_DEFINITION"));
            return false;
        }
        var inheritance = definitions.snapshot().inheritanceDefinitions().get(definition.inheritanceProfile());
        if (inheritance == null) {
            repository.put(session.withStatus("INVALID_INHERITANCE"));
            return false;
        }
        long seed = session.sessionId().getMostSignificantBits() ^ session.sessionId().getLeastSignificantBits();
        var offspring = family.createOffspring(session.ownerId(), session.participantPokemonIds(), inheritance, seed);
        if (offspring.isEmpty()) {
            deadlines.add(new Deadline(Math.addExact(nowMillis, DELIVERY_RETRY_MILLIS), session.sessionId()));
            return false;
        }
        UUID pokemonId = offspring.get().getUuid();
        int generation = session.participantPokemonIds().stream()
                .map(lineage::get)
                .flatMap(Optional::stream)
                .map(LineageRecord::generation)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        UUID householdId = session.participantPokemonIds().stream()
                .map(parent -> relationships.existing(session.ownerId(), parent).map(state -> state.householdId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        lineage.put(new LineageRecord(
                pokemonId,
                session.ownerId(),
                session.participantPokemonIds(),
                householdId,
                generation,
                nowMillis,
                definition.inheritanceProfile(),
                session.sessionId()
        ));
        repository.put(session.completed(pokemonId));
        relationships.state(session.ownerId(), pokemonId);
        if (householdId != null) relationships.assignHousehold(session.ownerId(), pokemonId, householdId);
        return true;
    }

    public List<DaycareSession> sessions(UUID ownerId) { return repository.byOwner(ownerId); }
    public Optional<DaycareSession> session(UUID id) { return repository.get(id); }

    private record Deadline(long dueAt, UUID sessionId) {}
    public enum StartResult { STARTED, UNKNOWN_DEFINITION, ALREADY_ACTIVE, NOT_OWNED, NOT_PARTNER, COST_FAILED, INVALID_PARTICIPANTS }
}
