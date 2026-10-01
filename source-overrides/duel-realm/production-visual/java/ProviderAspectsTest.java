package vn.svarcade.tcg.integration;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProviderAspectsTest {
    private static ProviderAspects.Variant variant(List<String> aspects,String poser) {
        return new ProviderAspects.Variant("addon:example",aspects,"addon",poser+".geo",poser,"test");
    }
    @Test void trackedMetadataDoesNotHideTheDeclaredActionBundle() {
        var mega=variant(List.of("mega_x"),"addon:mega_x");
        var provider=new ProviderAspects(List.of(mega));
        assertEquals(mega,provider.findAnimationVariant("addon:example",List.of("mega_x","genderless")).orElseThrow());
        assertTrue(provider.find("addon:example",List.of("mega_x","genderless")).isEmpty());
        assertTrue(provider.findAnimationVariant("addon:other",List.of("mega_x","genderless")).isEmpty());
    }
    @Test void mostSpecificVariantWinsRegardlessOfDeclarationOrder() {
        var mega=variant(List.of("mega_x"),"addon:mega_x");
        var shiny=variant(List.of("mega_x","shiny"),"addon:shiny");
        for(var values:List.of(List.of(mega,shiny),List.of(shiny,mega))) {
            var provider=new ProviderAspects(values);
            assertEquals(shiny,provider.findAnimationVariant("addon:example",List.of("genderless","shiny","mega_x")).orElseThrow());
            assertEquals(mega,provider.findAnimationVariant("addon:example",List.of("mega_x")).orElseThrow());
        }
    }
    @Test void unknownOrAmbiguousVariantDoesNotBorrowAnUnrelatedBaseAnimation() {
        var provider=new ProviderAspects(List.of(variant(List.of(),"addon:base"),
                variant(List.of("mega_x"),"addon:x"),variant(List.of("mega_y"),"addon:y")));
        assertTrue(provider.findAnimationVariant("addon:example",List.of("unavailable","genderless")).isEmpty());
        assertTrue(provider.findAnimationVariant("addon:example",List.of("mega_x","mega_y","genderless")).isEmpty());
    }
}
