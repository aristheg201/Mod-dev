package vn.svframe.svrelationships.fabric.relationship;

import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.fabric.config.ScheduleDefinitionService;
import vn.svframe.svrelationships.gameplay.ScheduleDefinition;

import java.util.Optional;
import java.util.UUID;

public final class ScheduleService {
    private final ScheduleDefinitionService definitions;
    private final GameplayDefinitionService gameplay;
    private final RelationshipService relationships;

    public ScheduleService(ScheduleDefinitionService definitions, GameplayDefinitionService gameplay, RelationshipService relationships) {
        this.definitions=definitions;this.gameplay=gameplay;this.relationships=relationships;
    }

    public Optional<ResolvedActivity> resolve(UUID playerId, UUID pokemonId, long worldTime) {
        var state=relationships.existing(playerId,pokemonId).orElse(null);if(state==null)return Optional.empty();
        String profileId=state.flag("schedule.profile");
        var snapshot=definitions.snapshot();
        if(profileId==null||profileId.isBlank()){
            String personality=state.personalityId();
            profileId=personality==null?snapshot.defaultProfile():snapshot.personalityProfiles().getOrDefault(personality,snapshot.defaultProfile());
        }
        ScheduleDefinition profile=snapshot.profiles().get(profileId);if(profile==null)return Optional.empty();
        int tick=Math.floorMod((int)(worldTime%24000L),24000);
        for(ScheduleDefinition.Entry entry:profile.entries())if(entry.contains(tick))return Optional.of(new ResolvedActivity(profileId,entry.id(),entry.activityId(),entry.materialize(),entry.messageKey()));
        return Optional.empty();
    }

    public boolean shouldMaterialize(UUID playerId,UUID pokemonId,long worldTime){return resolve(playerId,pokemonId,worldTime).map(ResolvedActivity::materialize).orElse(true);}
    public void setProfile(UUID playerId,UUID pokemonId,String profileId){if(profileId!=null&&!profileId.isBlank()&&!definitions.snapshot().profiles().containsKey(profileId))throw new IllegalArgumentException("Unknown schedule profile: "+profileId);var state=relationships.state(playerId,pokemonId);state.setFlag("schedule.profile",profileId);relationships.touch();}
    public record ResolvedActivity(String profileId,String entryId,String activityId,boolean materialize,String messageKey){}
}
