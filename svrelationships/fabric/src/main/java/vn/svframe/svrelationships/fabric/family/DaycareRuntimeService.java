package vn.svframe.svrelationships.fabric.family;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svrelationships.fabric.config.ConfigService;
import vn.svframe.svrelationships.fabric.config.DaycarePolicyService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.persistence.DaycareRepository;
import vn.svframe.svrelationships.fabric.persistence.LineageRepository;
import vn.svframe.svrelationships.fabric.relationship.RelationshipService;
import vn.svframe.svrelationships.fabric.reward.RelationshipRewardService;
import vn.svframe.svrelationships.family.DaycareEngine;
import vn.svframe.svrelationships.family.DaycareSession;
import vn.svframe.svrelationships.family.InheritanceDefinition;
import vn.svframe.svrelationships.family.LineageRecord;
import vn.svframe.svrelationships.integration.EconomyProvider;
import vn.svframe.svrelationships.integration.ProviderHub;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.SplittableRandom;
import java.util.UUID;

public final class DaycareRuntimeService {
    private final MinecraftServer server; private final ConfigService config; private final GameplayDefinitionService definitions; private final DaycarePolicyService policies;
    private final DaycareRepository repository; private final LineageRepository lineage; private final RelationshipService relationships; private final ProviderHub providers; private final RelationshipRewardService rewards;
    private final CobblemonFamilyService family; private final PriorityQueue<Deadline> deadlines = new PriorityQueue<>(Comparator.comparingLong(Deadline::dueAt));

    public DaycareRuntimeService(MinecraftServer server, ConfigService config, GameplayDefinitionService definitions, DaycarePolicyService policies,
                                 DaycareRepository repository, LineageRepository lineage, RelationshipService relationships,
                                 ProviderHub providers, RelationshipRewardService rewards) {
        this.server=server; this.config=config; this.definitions=definitions; this.policies=policies; this.repository=repository; this.lineage=lineage; this.relationships=relationships; this.providers=providers; this.rewards=rewards; this.family=new CobblemonFamilyService(server);
        repository.active().forEach(session -> deadlines.add(new Deadline(session.completeAtMillis(), session.sessionId())));
    }

    public StartResult start(UUID ownerId, String definitionId, List<UUID> participants, long nowMillis) {
        var definition=definitions.snapshot().daycareDefinitions().get(definitionId); if(definition==null)return StartResult.UNKNOWN_DEFINITION;
        var policy=policies.snapshot().policy(definitionId);
        long active=repository.byOwner(ownerId).stream().filter(s->s.definitionId().equals(definitionId)).filter(s->"ACTIVE".equals(s.status())||"REWARD_PENDING".equals(s.status())).count();
        if(active>=policy.maxActiveSessions())return StartResult.ALREADY_ACTIVE;
        for(UUID participant:participants)if(family.findOwned(ownerId,participant).isEmpty())return StartResult.NOT_OWNED;
        if("partner_family".equals(definition.mode())&&(participants.isEmpty()||!relationships.state(ownerId,participants.getFirst()).partner()))return StartResult.NOT_PARTNER;
        if(definition.cost()>0&&!charge(ownerId,definition.economyProvider(),definition.currency(),definition.cost()))return StartResult.COST_FAILED;
        DaycareSession session; try{session=new DaycareEngine(definitions.snapshot().daycareDefinitions()).start(ownerId,definitionId,participants,nowMillis);}catch(RuntimeException exception){return StartResult.INVALID_PARTICIPANTS;}
        repository.put(session); deadlines.add(new Deadline(session.completeAtMillis(),session.sessionId())); return StartResult.STARTED;
    }

    private boolean charge(UUID ownerId,String requestedProvider,String currency,long amount){EconomyProvider provider=null;if(requestedProvider!=null&&!requestedProvider.isBlank())provider=providers.economy(requestedProvider).orElse(null);if(provider==null)for(String id:config.snapshot().economyPriority()){var candidate=providers.economy(id);if(candidate.isPresent()&&candidate.get().available()){provider=candidate.get();break;}}return provider!=null&&provider.withdraw(ownerId,currency==null||currency.isBlank()?"default":currency,BigDecimal.valueOf(amount));}

    public void tick(long nowMillis){while(!deadlines.isEmpty()&&deadlines.peek().dueAt()<=nowMillis){Deadline deadline=deadlines.poll();repository.get(deadline.sessionId()).filter(session->session.due(nowMillis)).ifPresent(session->complete(session,nowMillis));}}
    public boolean completeNow(UUID sessionId,long nowMillis){Optional<DaycareSession> session=repository.get(sessionId);return session.isPresent()&&complete(session.get(),nowMillis);}

    private boolean complete(DaycareSession session,long nowMillis){
        if(!"ACTIVE".equals(session.status())&&!"REWARD_PENDING".equals(session.status()))return false;
        var definition=definitions.snapshot().daycareDefinitions().get(session.definitionId());if(definition==null){repository.put(session.withStatus("INVALID_DEFINITION"));return false;}
        InheritanceDefinition inheritance=definitions.snapshot().inheritanceDefinitions().get(definition.inheritanceProfile());if(inheritance==null){repository.put(session.withStatus("INVALID_INHERITANCE"));return false;}
        var policy=policies.snapshot().policy(session.definitionId()); ServerPlayerEntity player=server.getPlayerManager().getPlayer(session.ownerId());
        if(player==null){reschedule(session,nowMillis,policy.retryDelayMillis());return false;}
        UUID pokemonId=session.producedPokemonId();
        if(pokemonId==null){
            long seed=session.sessionId().getMostSignificantBits()^session.sessionId().getLeastSignificantBits();
            Optional<com.cobblemon.mod.common.pokemon.Pokemon> offspring;
            try{offspring=family.createOffspring(session.ownerId(),session.participantPokemonIds(),definition.participantRoles(),policy.offspringSpeciesSource(),inheritance,seed);}catch(IllegalArgumentException exception){repository.put(session.withStatus("INVALID_SPECIES_SOURCE"));return false;}
            if(offspring.isEmpty()){reschedule(session,nowMillis,policy.retryDelayMillis());return false;}
            pokemonId=offspring.get().getUuid(); recordOffspring(session,definition.inheritanceProfile(),inheritance,pokemonId,nowMillis,seed); session=session.awaitingReward(pokemonId); repository.put(session);
        }
        if(definition.rewardProfile().isBlank()){repository.put(session.completed(pokemonId));return true;}
        var reward=rewards.grantOnce(player,pokemonId,definition.rewardProfile(),session.sessionId(),"daycare:"+session.definitionId());
        if(reward==RelationshipRewardService.ClaimResult.DELIVERED||reward==RelationshipRewardService.ClaimResult.ALREADY_CLAIMED){repository.put(session.completed(pokemonId));return true;}
        if(reward==RelationshipRewardService.ClaimResult.UNKNOWN_PROFILE){repository.put(session.withStatus("INVALID_REWARD_PROFILE"));return false;}
        repository.put(session.awaitingReward(pokemonId));reschedule(session,nowMillis,policy.retryDelayMillis());return false;
    }

    private void recordOffspring(DaycareSession session,String inheritanceProfile,InheritanceDefinition inheritance,UUID pokemonId,long nowMillis,long seed){
        int generation=session.participantPokemonIds().stream().map(lineage::get).flatMap(Optional::stream).map(LineageRecord::generation).max(Integer::compareTo).orElse(0)+1;
        UUID householdId=session.participantPokemonIds().stream().map(parent->relationships.existing(session.ownerId(),parent).map(state->state.householdId()).orElse(null)).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        lineage.put(new LineageRecord(pokemonId,session.ownerId(),session.participantPokemonIds(),householdId,generation,nowMillis,inheritanceProfile,session.sessionId()));
        relationships.state(session.ownerId(),pokemonId); inheritPersonality(session.ownerId(),pokemonId,session.participantPokemonIds(),inheritance,seed); if(householdId!=null)relationships.assignHousehold(session.ownerId(),pokemonId,householdId);
    }

    private void reschedule(DaycareSession session,long nowMillis,long delay){deadlines.add(new Deadline(Math.addExact(nowMillis,delay),session.sessionId()));}
    private void inheritPersonality(UUID ownerId,UUID offspringId,List<UUID> parents,InheritanceDefinition inheritance,long seed){String mode=inheritance.personalityMode()==null?"none":inheritance.personalityMode().toLowerCase(java.util.Locale.ROOT);if("none".equals(mode))return;List<String> inherited=new ArrayList<>();for(UUID parentId:parents)relationships.existing(ownerId,parentId).map(state->state.personalityId()).filter(java.util.Objects::nonNull).filter(value->!value.isBlank()).ifPresent(inherited::add);inherited.sort(String::compareTo);SplittableRandom random=new SplittableRandom(seed^0x535652504552534fL);String selected=null;if(("parent".equals(mode)||"parent_or_random".equals(mode))&&!inherited.isEmpty())selected=inherited.get(random.nextInt(inherited.size()));if(selected==null&&("random".equals(mode)||"parent_or_random".equals(mode))){List<String> available=definitions.snapshot().personalities().keySet().stream().sorted().toList();if(!available.isEmpty())selected=available.get(random.nextInt(available.size()));}if(selected!=null)relationships.setPersonality(ownerId,offspringId,selected);}
    public List<DaycareSession> sessions(UUID ownerId){return repository.byOwner(ownerId);} public Optional<DaycareSession> session(UUID id){return repository.get(id);} private record Deadline(long dueAt,UUID sessionId){}
    public enum StartResult{STARTED,UNKNOWN_DEFINITION,ALREADY_ACTIVE,NOT_OWNED,NOT_PARTNER,COST_FAILED,INVALID_PARTICIPANTS}
}
