package vn.svarcade.tcg.fabric;

import org.junit.jupiter.api.Test;
import vn.svarcade.tcg.data.Catalog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class CobblemonCatalogHydratorTest {
    @Test void registryExpansionAddsOfficialFakemonAndDeckTemplates() throws Exception {
        Catalog base=Catalog.load(Path.of("build/nonexistent-catalog-for-test.json"));
        List<CobblemonCatalogHydrator.SpeciesDescriptor> species=new ArrayList<>();
        for(int i=1;i<=1025;i++)species.add(new CobblemonCatalogHydrator.SpeciesDescriptor("cobblemon:test"+i,"cobblemon","test"+i,"Test "+i,i,i%2==0?"fire":"water",420+(i%180)));
        for(int i=1;i<=12;i++)species.add(new CobblemonCatalogHydrator.SpeciesDescriptor("myaddon:fakemon"+i,"myaddon","fakemon"+i,"Fakemon "+i,0,"ghost",500));
        species.add(new CobblemonCatalogHydrator.SpeciesDescriptor("cobblemon:test25#alolan","cobblemon","test25","Test 25 · Alolan",25,"dark",510,List.of("alolan")));
        // Official family members exercise data selectors without a live Minecraft registry.
        for(String path:List.of("arceus","dialga","palkia","giratina","eevee","nihilego","omanyte","bulbasaur"))
            species.add(new CobblemonCatalogHydrator.SpeciesDescriptor("cobblemon:"+path,"cobblemon",path,path,1,"psychic",680));
        species.add(new CobblemonCatalogHydrator.SpeciesDescriptor("cobblemon:test25#mega","cobblemon","test25","Test 25 · Mega",25,"water",600,List.of("mega")));
        Catalog expanded=CobblemonCatalogHydrator.expandFromDescriptors(base,species);
        assertTrue(expanded.cards().containsKey("test1"));
        assertTrue(expanded.cards().containsKey("fakemon__myaddon__fakemon1"));
        assertTrue(expanded.cards().size()>=1025);
        for(String template:List.of("kanto","galar","alola","hisui","creation","mega","ultra_space","distortion_world","eeveelution","fossil"))assertTrue(expanded.starters().containsKey(template),template);
        assertTrue(expanded.starters().containsKey("region_kanto"));
        assertTrue(expanded.starters().containsKey("fakemon_myaddon"));
        assertTrue(expanded.starters().containsKey("fakemon_all"));
        assertTrue(expanded.banners().containsKey("fakemon_myaddon_pack"));
        assertTrue(expanded.banners().containsKey("fakemon_all_pack"));
        for(String template:List.of("starter","legendary","dark_shadow"))assertTrue(expanded.starters().containsKey(template),template);
        assertTrue(expanded.deckErrors(expanded.starters().get("fakemon_myaddon"),List.of(),false).isEmpty());
        for(var entry:expanded.starters().entrySet()) {
            if(entry.getKey().startsWith("type_")||entry.getKey().startsWith("fakemon_")||entry.getKey().startsWith("region_")) {
                long monsters=entry.getValue().stream().filter(id->expanded.card(id).category().equals("pokemon")).count();
                assertEquals(24,monsters,entry.getKey());
                assertTrue(expanded.deckErrors(entry.getValue(),List.of(),false).isEmpty(),entry.getKey());
            }
        }
        assertEquals("myaddon:fakemon1",expanded.card("fakemon__myaddon__fakemon1").species());
        assertTrue(expanded.cards().containsKey("test25__form__alolan"));
        assertEquals(List.of("alolan"),expanded.card("test25__form__alolan").aspects());
        assertTrue(expanded.starters().containsKey("forms_showcase"));
    }
}
