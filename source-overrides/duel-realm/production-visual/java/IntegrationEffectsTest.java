package vn.svarcade.tcg.duel;

import org.junit.jupiter.api.*;
import vn.svarcade.tcg.data.*;
import vn.svarcade.tcg.integration.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Focused server-side proofs. Fixtures provide data, never bypass activation/cost/resolution rules. */
class IntegrationEffectsTest {
    Catalog catalog;
    @BeforeEach void load() throws Exception {
        var base=Catalog.load(Path.of("absent-integration-test.json"));var r=base.rules();
        catalog=new Catalog(new Catalog.Rules(5,60,15,3,5,8000,5,5,1,300,r.rankedLimits(),r.effective()),base.cards(),base.banners(),base.rewards(),base.dealers(),base.starters());
    }
    Catalog specialCatalog(){var variants=SpecialAspectCards.definitions().stream().map(d->new ProviderAspects.Variant(d.species(),d.requiredAspects(),"mega_showdown","verified-by-runtime","",d.id())).toList();
        return SpecialAspectCards.apply(catalog,new IntegrationCapabilities(Set.of("cobblemon","mega_showdown")),new ProviderAspects(variants),(s,a)->true);}
    Duel duel(Catalog c){var d=new Duel(c,List.of("charmander","squirtle","protect","potion","research"),List.of(),List.of("charmander","squirtle","protect","potion","research"),List.of(),new Random(7));d.qaReset(0,3);for(int seat=0;seat<2;seat++)for(int i=0;i<12;i++)d.qaAdd(seat,"charmander",Duel.Zone.DECK);return d;}
    void act(Duel d,int seat,String kind,String token,String target){d.act(seat,new Duel.Action(kind,token,target),d.revision());}
    void resolve(Duel d){int guard=50;while(!d.view(0).chain().isEmpty()&&guard-->0)act(d,d.view(0).priority(),"pass","","");assertTrue(d.view(0).chain().isEmpty());}
    void open(Duel d){int guard=80;while(!d.view(0).open()&&guard-->0){act(d,d.view(0).priority(),"pass","","");}assertTrue(d.view(0).open());}
    void advance(Duel d,String phase){int guard=80;while(!d.view(0).phase().equals(phase)&&guard-->0){open(d);act(d,d.view(0).turnPlayer(),"next","","");open(d);}assertEquals(phase,d.view(0).phase());}
    Catalog.Effect effect(String json){return new Gson().fromJson(json,Catalog.Effect.class);}
    void use(Duel d,Duel.Piece p,Catalog.Effect e,String target){e.spec().validate();p.grantedEffect=e;act(d,p.controller,"activate",p.token,target);resolve(d);}
    @Test void allCardsHaveDistinctExecutableGraphsAndChoreography(){var c=CardIdentities.apply(specialCatalog());CardIdentities.validateUniqueness(c);AuthoredEffectValidator.validate(c);assertEquals(24,c.cards().keySet().stream().filter(id->id.startsWith("special_")).count());}
    @Test void generatedFormsInheritTheirActualElementForVisualStages(){
        var base=catalog.card("charmander");var form=new Catalog.Card("fixture_form",base.name(),base.category(),base.species(),List.of("provider-owned-form"),base.type(),base.family(),base.evolvesFrom(),base.extra(),base.level(),base.power(),base.text(),base.set(),base.rarity(),base.sources(),null,base.triggers(),base.modifiers());
        var cards=new LinkedHashMap<>(catalog.cards());cards.put(form.id(),form);var c=EffectContent.apply(new Catalog(catalog.rules(),cards,catalog.banners(),catalog.rewards(),catalog.dealers(),catalog.starters()));
        var actual=c.card(form.id()).effect().spec();assertNull(actual.vfx().profile());assertTrue(actual.vfx().stages().stream().allMatch(stage->"fire".equals(stage.profile())));assertDoesNotThrow(()->AuthoredEffectValidator.validate(c));
    }
    @Test void providerAbsenceAndInvalidAspectOmitSpecialCards(){var c=SpecialAspectCards.apply(catalog,new IntegrationCapabilities(Set.of("cobblemon")),new ProviderAspects(List.of()),(s,a)->true);assertEquals(catalog.cards().size(),c.cards().size());var invalid=SpecialAspectCards.apply(catalog,new IntegrationCapabilities(Set.of("cobblemon","mega_showdown")),new ProviderAspects(List.of()),(s,a)->true);assertEquals(catalog.cards().size(),invalid.cards().size());}
    @Test void registryReloadIsIdempotent(){var c=CardIdentities.apply(catalog);assertEquals(c.cards(),CardIdentities.apply(c).cards());}
    @Test void semanticCapabilitiesAreOptionalAndClientFree(){assertTrue(new IntegrationCapabilities(Set.of()).capabilities().isEmpty());assertTrue(new IntegrationCapabilities(Set.of("cobblemon","mega_showdown","placeholder-api","beconomy","cobbledollars","impactor")).capabilities().containsAll(Set.of("COBBLEMON_ASPECTS","MEGA_SHOWDOWN","PLACEHOLDERS","BEconomy".toUpperCase(Locale.ROOT),"COBBLEDOLLARS","IMPACTOR")));}
    @Test void placeholderFallbackNeverLeaksKeysOrParsesPlayerMarkup(){assertEquals("Xin chào —",PlaceholderValues.resolve("Xin chào %svarcade_tcg:player%",key->null));assertEquals("LP: 7200",PlaceholderValues.resolve("LP: %svarcade_tcg:lp%",key->"7200"));assertEquals("—",PlaceholderValues.resolve("%bad:key%",key->"%still:raw%"));assertEquals("\\<red>Player",PlaceholderValues.escapeMini("<red>Player"));assertFalse(PlaceholderValues.TOKEN.matcher(PlaceholderValues.resolve("%svarcade_tcg:lp%",key->"7200")).find());}
    @Test void signatureAdditionalAbilityUsesRealCostsAndIndependentLimit(){var d=duel(catalog);var p=d.qaAdd(0,"charmander",Duel.Zone.FIELD);int before=d.view(0).life().getFirst();act(d,0,"activate_stage",p.token,"signature|");resolve(d);assertEquals(before-300-p.card.level()*50,d.view(0).life().getFirst());open(d);assertThrows(IllegalArgumentException.class,()->act(d,0,"activate_stage",p.token,"signature|"));assertThrows(IllegalArgumentException.class,()->act(d,0,"activate_stage",p.token,"invented|"));}
    @Test void allTwentyFourSpecialPrimaryGraphsExecuteWithPaidCounters(){var c=specialCatalog();for(var definition:SpecialAspectCards.definitions()){
        var d=duel(c);var a=d.qaAdd(0,"charmander",Duel.Zone.FIELD);var b=d.qaAdd(0,"squirtle",Duel.Zone.FIELD);var source=d.qaAdd(0,definition.id(),Duel.Zone.HAND);
        act(d,0,"play",source.token,a.token+","+b.token);resolve(d);assertEquals(1,source.counters.get(definition.effect().spec().costs().getFirst().counter()),definition.id());open(d);
        var target=d.qaAdd(definition.effect().target().equals("ally")?0:1,"squirtle",Duel.Zone.FIELD);d.qaAdd(0,"charmander",Duel.Zone.DISCARD);
        act(d,0,"activate",source.token,definition.effect().target().equals("none")?"":target.token);resolve(d);
        assertTrue(d.view(0).cues().stream().anyMatch(v->v.semantic().equals("CHAIN_RESOLVE")),definition.id());
        assertTrue(source.counters.get(definition.effect().spec().costs().getFirst().counter())<=1,definition.id());
    }}
    @Test void oncePerCardNamePreventsSecondCopyAndLeavesCountersUntouched(){var c=specialCatalog();var d=duel(c);var first=d.qaAdd(0,"special_mewtwo_mega_x",Duel.Zone.FIELD);var second=d.qaAdd(0,"special_mewtwo_mega_x",Duel.Zone.FIELD);
        var generate=effect("{\"operation\":\"composite\",\"speed\":2,\"target\":\"none\",\"phases\":[\"MAIN1\"],\"spec\":{\"triggers\":[\"ON_ACTIVATE\"],\"operations\":[{\"type\":\"ADD_COUNTER\",\"target\":\"ALL_ALLIES\",\"amount\":1,\"flags\":{\"counter\":\"willpower\"}}]}}");
        var helper=d.qaAdd(0,"research",Duel.Zone.HAND);use(d,helper,generate,"");open(d);act(d,0,"activate",first.token,first.token);resolve(d);open(d);assertThrows(IllegalArgumentException.class,()->act(d,0,"activate",second.token,second.token));assertEquals(1,second.counters.get("willpower"));}
    @Test void delayedEffectRemembersASelectedCardUntilResolution(){var d=duel(catalog);var enemy=d.qaAdd(1,"squirtle",Duel.Zone.FIELD);var source=d.qaAdd(0,"research",Duel.Zone.HAND);
        var e=effect("{\"operation\":\"composite\",\"speed\":2,\"target\":\"enemy\",\"phases\":[\"MAIN1\"],\"spec\":{\"triggers\":[\"ON_ACTIVATE\"],\"operations\":[{\"type\":\"REMEMBER\",\"target\":\"TARGET\",\"flags\":{\"memory\":\"chosen\"}},{\"type\":\"DELAY\",\"duration\":\"NEXT_TURN_END\",\"flags\":{\"trigger\":\"ON_TURN_END\"},\"children\":[{\"type\":\"RETURN_HAND\",\"target\":\"REMEMBERED\",\"flags\":{\"memory\":\"chosen\"}}]}]}}");
        use(d,source,e,enemy.token);assertEquals(Duel.Zone.FIELD,enemy.zone);advance(d,"END");open(d);act(d,0,"next","","");open(d);assertEquals(Duel.Zone.HAND,enemy.zone);}
    @Test void chainTargetLegalityIsRecheckedButIndependentDrawStillResolves(){var d=duel(catalog);var enemy=d.qaAdd(1,"squirtle",Duel.Zone.FIELD);var first=d.qaAdd(0,"research",Duel.Zone.HAND);first.grantedEffect=effect("{\"operation\":\"composite\",\"speed\":2,\"target\":\"enemy\",\"phases\":[\"MAIN1\"],\"spec\":{\"triggers\":[\"ON_ACTIVATE\"],\"operations\":[{\"type\":\"DESTROY\",\"target\":\"TARGET\"},{\"type\":\"DRAW\",\"amount\":1}]}}");
        act(d,0,"activate",first.token,enemy.token);var recall=d.qaAdd(1,"research",Duel.Zone.HAND);recall.grantedEffect=effect("{\"operation\":\"composite\",\"speed\":2,\"target\":\"ally\",\"phases\":[\"MAIN1\"],\"spec\":{\"triggers\":[\"ON_ACTIVATE\"],\"operations\":[{\"type\":\"RETURN_HAND\",\"target\":\"TARGET\"}]}}");
        act(d,1,"activate",recall.token,enemy.token);resolve(d);assertEquals(Duel.Zone.HAND,enemy.zone);assertEquals(1,d.view(0).handCounts().getFirst());assertEquals(List.of(2,1),d.view(0).cues().stream().filter(v->v.semantic().equals("CHAIN_RESOLVE")).map(Duel.Cue::link).toList());}
    @Test void effectMemoryTracksCardsDestroyedByThisResolution(){var d=duel(catalog);var enemy=d.qaAdd(1,"squirtle",Duel.Zone.FIELD);var source=d.qaAdd(0,"research",Duel.Zone.HAND);use(d,source,effect("{\"operation\":\"composite\",\"speed\":2,\"target\":\"enemy\",\"phases\":[\"MAIN1\"],\"spec\":{\"triggers\":[\"ON_ACTIVATE\"],\"operations\":[{\"type\":\"DESTROY\",\"target\":\"TARGET\"},{\"type\":\"BANISH\",\"target\":\"DESTROYED_THIS_RESOLUTION\"}]}}"),enemy.token);assertEquals(Duel.Zone.BANISHED,enemy.zone);}
    @Test void rememberedTargetIsNotReusedAfterItChangesZone(){var d=duel(catalog);var enemy=d.qaAdd(1,"squirtle",Duel.Zone.FIELD);var source=d.qaAdd(0,"research",Duel.Zone.HAND);use(d,source,effect("{\"operation\":\"composite\",\"speed\":2,\"target\":\"enemy\",\"phases\":[\"MAIN1\"],\"spec\":{\"triggers\":[\"ON_ACTIVATE\"],\"operations\":[{\"type\":\"REMEMBER\",\"target\":\"TARGET\"},{\"type\":\"RETURN_HAND\",\"target\":\"TARGET\"},{\"type\":\"BANISH\",\"target\":\"REMEMBERED\"}]}}"),enemy.token);assertEquals(Duel.Zone.HAND,enemy.zone);}
    @Test void privateInspectionDoesNotExposeSetCardToSpectator(){var d=duel(catalog);var trap=d.qaAdd(0,"attack_mirror",Duel.Zone.HAND);act(d,0,"play",trap.token,"SET");open(d);
        advance(d,"END");open(d);act(d,0,"next","","");open(d);advance(d,"MAIN1");
        var source=d.qaAdd(1,"research",Duel.Zone.HAND);use(d,source,effect("{\"operation\":\"composite\",\"speed\":2,\"target\":\"none\",\"phases\":[\"MAIN1\"],\"spec\":{\"triggers\":[\"ON_ACTIVATE\"],\"operations\":[{\"type\":\"INSPECT_SET\",\"target\":\"ENEMY_CARD\"}]}}"),"");assertEquals(trap.card.name(),d.view(1).cards().stream().filter(v->v.token().equals(trap.token)).findFirst().orElseThrow().name());assertEquals("Set card",d.spectatorView().cards().stream().filter(v->v.token().equals(trap.token)).findFirst().orElseThrow().name());}
    @Test void replacementPreventsActualDestructionAndSpendsCounters(){var d=duel(catalog);var victim=d.qaAdd(0,"squirtle",Duel.Zone.FIELD);var protector=d.qaAdd(0,"charmander",Duel.Zone.FIELD);
        var e=effect("{\"operation\":\"composite\",\"speed\":2,\"target\":\"none\",\"phases\":[\"MAIN1\"],\"spec\":{\"triggers\":[\"ON_ACTIVATE\"],\"operations\":[{\"type\":\"ADD_COUNTER\",\"target\":\"SELF\",\"amount\":2,\"flags\":{\"counter\":\"armor\"}}],\"stages\":[{\"id\":\"barrier\",\"sourceZones\":[\"FIELD\"],\"listenAny\":true,\"effect\":{\"operation\":\"composite\",\"speed\":3,\"target\":\"none\",\"phases\":[\"MAIN1\"],\"oncePerTurn\":true,\"spec\":{\"triggers\":[\"WOULD_DESTROY\"],\"costs\":[{\"type\":\"COUNTER_COST\",\"amount\":2,\"counter\":\"armor\"}],\"conditions\":[{\"type\":\"EVENT_CONTROLLER\",\"value\":\"ALLY\"}],\"operations\":[{\"type\":\"REPLACE_DESTROY\",\"target\":\"EVENT_CARD\"}]}}}]}}");
        use(d,protector,e,"");open(d);act(d,0,"pass","","");var destroy=d.qaAdd(1,"shatter_gate",Duel.Zone.HAND);var removal=destroy.card.effect();destroy.grantedEffect=new Catalog.Effect(removal.operation(),removal.amount(),2,removal.lifeCost(),removal.target(),removal.phases(),removal.oncePerTurn(),removal.spec());act(d,1,"activate",destroy.token,victim.token);resolve(d);assertEquals(Duel.Zone.FIELD,victim.zone);assertEquals(0,protector.counters.get("armor"));assertTrue(d.view(0).cues().stream().anyMatch(v->v.semantic().equals("SHIELD")));
    }
    @Test void redirectionChangesThePreviousLinksLegalTarget(){var d=duel(catalog);var original=d.qaAdd(1,"squirtle",Duel.Zone.FIELD);var redirected=d.qaAdd(1,"charmander",Duel.Zone.FIELD);var destroy=d.qaAdd(0,"shatter_gate",Duel.Zone.HAND);act(d,0,"activate",destroy.token,original.token);
        var e=effect("{\"operation\":\"composite\",\"speed\":2,\"target\":\"ally\",\"phases\":[\"MAIN1\"],\"spec\":{\"triggers\":[\"ON_ACTIVATE\"],\"operations\":[{\"type\":\"REDIRECT_TARGET\",\"target\":\"TARGET\"}]}}");var helper=d.qaAdd(1,"research",Duel.Zone.HAND);helper.grantedEffect=e;act(d,1,"activate",helper.token,redirected.token);resolve(d);assertEquals(Duel.Zone.FIELD,original.zone);assertEquals(Duel.Zone.DISCARD,redirected.zone);
    }
    @Test void everyGeneratedSignatureExecutesThroughTheAuthoritativeStagePath(){
        for(var card:catalog.cards().values()){
            if(card.effect()==null||card.effect().spec()==null||card.effect().spec().stages().stream().noneMatch(s->s.id().equals("signature")))continue;
            var d=duel(catalog);var source=d.qaAdd(0,card.id(),card.category().equals("pokemon")?Duel.Zone.FIELD:card.category().equals("stadium")?Duel.Zone.STADIUM:Duel.Zone.SUPPORT);
            d.qaAdd(1,"squirtle",Duel.Zone.FIELD);d.qaAdd(0,"charmander",Duel.Zone.DISCARD);d.qaAdd(0,"potion",Duel.Zone.HAND);
            if(card.category().equals("reaction")){var spell=d.qaAdd(0,"flamethrower",Duel.Zone.HAND);act(d,0,"activate",spell.token,"");act(d,1,"pass","","");}
            act(d,0,"activate_stage",source.token,"signature|");resolve(d);assertTrue(d.view(0).cues().stream().anyMatch(c->c.semantic().equals("CHAIN_RESOLVE")&&c.source().equals(source.token)),card.id());
        }
    }
    @Test void invalidAuthoredDataIncludesCardAndField(){var c=SpecialAspectCards.definitions().getFirst();assertThrows(IllegalArgumentException.class,()->new EffectSpec(List.of("INVENTED_TRIGGER"),List.of(),List.of(),null,List.of(new EffectSpec.Operation("DRAW",1,"SELF",null,null,null,null,null,null)),false,false,null,null).validate());assertTrue(c.effect().spec().textKey().contains(c.id()));}
}
