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
    private static Map<String,List<String>> load() {
        try(var in=PokemonDuelAnimationResolver.class.getResourceAsStream("/data/svarcade_tcg/animation_semantics.json")) {
            return new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8),new TypeToken<Map<String,List<String>>>(){}.getType());
        }catch(Exception e){return Map.of();}
    }
    public static Resolution play(PokemonEntity entity,String intent) {
        var state=(PokemonClientDelegate)entity.getDelegate();
        try {
            var model=VaryingModelRepository.INSTANCE.getPoser(entity.getPokemon().getSpecies().getResourceIdentifier(),state);
            state.setCurrentModel(model);
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
        }catch(RuntimeException ignored){/* Preserve the provider's normal idle pose, never force a missing animation. */}
        return new Resolution(intent,"provider_idle",0,false);
    }
    private PokemonDuelAnimationResolver() {}
}
