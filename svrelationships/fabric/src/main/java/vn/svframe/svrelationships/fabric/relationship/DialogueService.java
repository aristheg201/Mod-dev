package vn.svframe.svrelationships.fabric.relationship;

import vn.svframe.svrelationships.fabric.config.DialogueDefinitionService;
import vn.svframe.svrelationships.fabric.config.GameplayDefinitionService;
import vn.svframe.svrelationships.gameplay.DialogueDefinition;

import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

public final class DialogueService {
    private final DialogueDefinitionService definitions;private final GameplayDefinitionService gameplay;private final RelationshipService relationships;
    public DialogueService(DialogueDefinitionService definitions,GameplayDefinitionService gameplay,RelationshipService relationships){this.definitions=definitions;this.gameplay=gameplay;this.relationships=relationships;}
    public Result select(UUID playerId,UUID pokemonId,String dialogueId,long nowMillis){
        DialogueDefinition definition=definitions.snapshot().definitions().get(dialogueId);if(definition==null)return new Result(Status.UNKNOWN_DEFINITION,null);
        var state=relationships.state(playerId,pokemonId);String cooldownId="dialogue:"+dialogueId;if(state.cooldownUntil(cooldownId)>nowMillis)return new Result(Status.COOLDOWN,null);
        if(!definition.requiredRoute().isBlank()){String route=relationships.currentRoute(playerId,pokemonId,definition.requiredRoute());if(!definition.requiredState().isBlank()&&!definition.requiredState().equals(route))return new Result(Status.REQUIREMENTS,null);}
        if(!definition.requiredPersonalityTags().isEmpty()){if(state.personalityId()==null)return new Result(Status.REQUIREMENTS,null);var personality=gameplay.snapshot().personalities().get(state.personalityId());Set<String> tags=personality==null?Set.of():personality.tags();if(!tags.containsAll(definition.requiredPersonalityTags()))return new Result(Status.REQUIREMENTS,null);}
        long total=0;for(DialogueDefinition.Entry entry:definition.entries())total=Math.addExact(total,entry.weight());
        long bucket=definition.cooldownMillis()>0?Math.floorDiv(nowMillis,definition.cooldownMillis()):nowMillis;
        long seed=state.relationshipId().getMostSignificantBits()^state.relationshipId().getLeastSignificantBits()^dialogueId.hashCode()^bucket;
        long roll=new SplittableRandom(seed).nextLong(total),cursor=0;DialogueDefinition.Entry selected=null;for(DialogueDefinition.Entry entry:definition.entries()){cursor+=entry.weight();if(roll<cursor){selected=entry;break;}}
        if(selected==null)return new Result(Status.REQUIREMENTS,null);if(definition.cooldownMillis()>0)relationships.setCooldown(playerId,pokemonId,cooldownId,Math.addExact(nowMillis,definition.cooldownMillis()));return new Result(Status.SUCCESS,selected.messageKey());
    }
    public enum Status{SUCCESS,UNKNOWN_DEFINITION,COOLDOWN,REQUIREMENTS}public record Result(Status status,String messageKey){}
}
