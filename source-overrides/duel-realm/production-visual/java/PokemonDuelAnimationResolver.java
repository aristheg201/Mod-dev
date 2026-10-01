package vn.svarcade.tcg.client.render;

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Semantic requests resolve against the effective provider, including addon named animations. */
public final class PokemonDuelAnimationResolver {
    public record Resolution(String intent,String animation,float duration,boolean nativeAnimation) {}
    private static final Map<String,List<String>> ALIASES=load();
    private static final Map<PokemonEntity,String> MODELS=new WeakHashMap<>();
    private static final Set<String> DIAGNOSTICS=new HashSet<>();
    public static void invalidate(){MODELS.clear();DIAGNOSTICS.clear();}
    private static Map<String,List<String>> load() {
        try(var in=PokemonDuelAnimationResolver.class.getResourceAsStream("/data/svarcade_tcg/animation_semantics.json")) {
            return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8),new TypeToken<Map<String,List<String>>>(){}.getType());
        }catch(Exception e){return Map.of();}
    }
    public static Resolution play(PokemonEntity entity,String intent) {
        var state=(PokemonClientDelegate)entity.getDelegate();
        try {
            String signature=entity.getPokemon().getSpecies().getResourceIdentifier()+"#"+String.join("+",new TreeSet<>(entity.getPokemon().getAspects()));
            var model=state.getCurrentModel();
            if(model==null||!signature.equals(MODELS.get(entity))){
                state.setPrimaryAnimation(null);state.getActiveAnimations().clear();state.setCurrentPose(null);
                model=VaryingModelRepository.INSTANCE.getPoser(entity.getPokemon().getSpecies().getResourceIdentifier(),state);state.setCurrentModel(model);MODELS.put(entity,signature);
            }
            if(state.getCurrentPose()==null)state.setPoseToFirstSuitable(PoseType.STAND);
            if(intent.equals("IDLE"))return new Resolution(intent,"provider_idle",0,false);
            LinkedHashSet<String> exposed=new LinkedHashSet<>(model.getAnimations().keySet());
            var pose=(com.cobblemon.mod.common.client.render.models.blockbench.pose.Pose)model.getPoses().get(state.getCurrentPose());if(pose!=null)exposed.addAll(pose.getNamedAnimations().keySet());
            List<String> names=ALIASES.getOrDefault(intent,List.of(intent.toLowerCase(Locale.ROOT)));
            LinkedHashSet<String> candidates=new LinkedHashSet<>();
            for(String name:names){
                exposed.stream().filter(key->key.equalsIgnoreCase(name)||key.toLowerCase(Locale.ROOT).endsWith("_"+name)).forEach(candidates::add);
                if(name.equals("cry")||name.equals("faint"))candidates.add(name);
            }
            for(String name:candidates) {
                if(Set.of("ATTACK_PHYSICAL","ATTACK_SPECIAL","CAST_STATUS","CHARGE","PROJECTILE_CAST","DASH","HIT","HEAVY_HIT","FAINT").contains(intent)&&name.toLowerCase(Locale.ROOT).contains("cry"))continue;
                try {
                    var animation=model.getAnimation(state,name,state.getRuntime());
                    if(animation==null)continue;
                    if(animation instanceof PrimaryAnimation primary)state.addPrimaryAnimation(primary);
                    else state.addActiveAnimation(animation,s->kotlin.Unit.INSTANCE);
                    float duration=Math.max(.2f,Math.min(4f,animation.getDuration()));
                    org.slf4j.LoggerFactory.getLogger("cardworlds-animation").info("CARDWORLDS_NATIVE_ANIMATION intent={} resolved={} duration={} provider={}",intent,name,duration,entity.getPokemon().getSpecies().getResourceIdentifier());
                    return new Resolution(intent,name,duration,true);
                }catch(RuntimeException ignored){/* Optional animation expression from an addon may be absent. */}
            }
            // A legacy poser may omit a registered action in its own provider animation bundle.
            // The effective client poser uses tracked aspects, including form-derived aspects.
            var variant=vn.svarcade.tcg.integration.CardWorldsIntegrations.aspects().find(entity.getPokemon().getSpecies().getResourceIdentifier().toString(),state.getCurrentAspects());
            if(variant.isPresent()&&!variant.get().poser().isBlank()){
                String group=variant.get().poser();int colon=group.indexOf(':');if(colon>=0)group=group.substring(colon+1);
                for(String name:names){
                    if(Set.of("ATTACK_PHYSICAL","ATTACK_SPECIAL","CAST_STATUS","CHARGE","PROJECTILE_CAST","DASH","HIT","HEAVY_HIT","FAINT").contains(intent)&&name.contains("cry"))continue;
                    var raw=com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository.INSTANCE.getAnimationOrNull(group,name);
                    // Repository keys retain the provider JSON's full Bedrock animation identifier.
                    if(raw==null)raw=com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository.INSTANCE.getAnimationOrNull(group,"animation."+group+"."+name);
                    if(raw==null)continue;
                    // Provider-owned loops can be played once for an action; keep the original bone/effect data.
                    if(raw.getShouldLoop()){
                        double duration=Math.clamp(raw.getAnimationLength()>0?raw.getAnimationLength():1.8,.65,3.0);
                        var once=raw.copy(false,duration,raw.getEffects(),raw.getBoneTimelines());once.setName(raw.getName());raw=once;
                    }
                    var animation=new com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation(raw);
                    state.addActiveAnimation(animation,s->kotlin.Unit.INSTANCE);
                    org.slf4j.LoggerFactory.getLogger("cardworlds-animation").info("CARDWORLDS_NATIVE_ANIMATION intent={} resolved={} duration={} provider={}",intent,raw.getName(),animation.getDuration(),entity.getPokemon().getSpecies().getResourceIdentifier());
                    return new Resolution(intent,group+":"+name,Math.max(.2f,Math.min(4f,animation.getDuration())),true);
                }
            }
            String key=entity.getPokemon().getSpecies().getResourceIdentifier()+"#"+state.getCurrentAspects()+"#"+intent;
            if(DIAGNOSTICS.add(key))org.slf4j.LoggerFactory.getLogger("cardworlds-animation").info("CARDWORLDS_ANIMATION_FALLBACK intent={} tracked={} forced={} variant={}",intent,state.getCurrentAspects(),entity.getPokemon().getForcedAspects(),variant);
        }catch(RuntimeException failure){
            String key=entity.getPokemon().getSpecies().getResourceIdentifier()+"#"+state.getCurrentAspects()+"#"+intent;
            if(DIAGNOSTICS.add(key))org.slf4j.LoggerFactory.getLogger("cardworlds-animation").warn("CARDWORLDS_ANIMATION_FALLBACK intent={} tracked={} forced={} reason={}",intent,state.getCurrentAspects(),entity.getPokemon().getForcedAspects(),failure.toString());
            /* Preserve the provider's normal idle pose, never force a missing animation. */
        }
        return new Resolution(intent,"provider_idle",0,false);
    }
    private PokemonDuelAnimationResolver() {}
}
