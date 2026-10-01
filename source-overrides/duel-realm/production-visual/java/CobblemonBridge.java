package vn.svarcade.tcg.integration;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import java.util.*;

/** Canonical species/aspect resolution uses loaded registries and provider-owned declarations. */
public final class CobblemonBridge {
    public record Descriptor(String species,String form,List<String> aspects,String gender,boolean shiny,String provider,boolean available) {}
    private static final Map<String,Descriptor> CACHE=new HashMap<>();
    private static final Set<String> WARNED=new HashSet<>();
    public static synchronized void invalidate(){CACHE.clear();}
    public static synchronized Descriptor resolve(String species,String form,List<String> aspects,String gender,boolean shiny,String provider) {
        String id=ProviderAspects.species(species);String key=ProviderAspects.key(id,aspects)+":"+form+":"+gender+":"+shiny+":"+provider;
        return CACHE.computeIfAbsent(key,ignored->{
            var actual=PokemonSpecies.getByIdentifier(net.minecraft.util.Identifier.of(id));
            boolean valid=actual!=null;
            if(valid&&!aspects.isEmpty()) {
                boolean knownForm=actual.getForms().stream().anyMatch(f->new HashSet<>(f.getAspects()).equals(new HashSet<>(aspects)));
                valid=knownForm||CardWorldsIntegrations.aspects().find(id,aspects).isPresent();
            }
            if(valid&&form!=null&&!form.isBlank())valid=actual.getForms().stream().anyMatch(f->f.getName().equalsIgnoreCase(form));
            String owner=CardWorldsIntegrations.aspects().find(id,aspects).map(ProviderAspects.Variant::provider).orElse("cobblemon");
            if(!valid&&WARNED.add(key))org.slf4j.LoggerFactory.getLogger("cardworlds-integrations").warn("CARDWORLDS_VARIANT_UNAVAILABLE species={} form={} aspects={} provider={}",id,form,aspects,provider);
            return new Descriptor(id,form,List.copyOf(aspects),gender,shiny,owner,valid);
        });
    }
    private CobblemonBridge(){}
}
