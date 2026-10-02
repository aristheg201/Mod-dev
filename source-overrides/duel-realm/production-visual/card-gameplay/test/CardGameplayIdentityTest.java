package vn.svarcade.tcg.duel;

import org.junit.jupiter.api.*;
import vn.svarcade.tcg.data.*;
import vn.svarcade.tcg.fabric.HeadlessCatalog;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Prove real gameplay results and complete registry coverage, never screenshot/text uniqueness. */
class CardGameplayIdentityTest {
    static Catalog catalog;
    @BeforeAll static void load()throws Exception{catalog=HeadlessCatalog.load();}
    Duel duel(){var d=new Duel(catalog,catalog.starters().get("crossroads"),List.of(),catalog.starters().get("crossroads"),List.of(),new Random(421));d.qaReset(0,3);for(int seat=0;seat<2;seat++)for(int i=0;i<40;i++)d.qaAdd(seat,"charmander",Duel.Zone.DECK);return d;}
    void act(Duel d,int seat,String kind,String source,String target){d.act(seat,new Duel.Action(kind,source,target),d.revision());}
    void resolve(Duel d){int guard=120;while(!d.view(0).chain().isEmpty()&&guard-->0)act(d,d.priority(),"pass","","");assertTrue(d.view(0).chain().isEmpty());}
    void open(Duel d){int guard=120;while(!d.view(0).open()&&guard-->0)act(d,d.priority(),"pass","","");assertTrue(d.view(0).open());}
    void use(Duel d,Duel.Piece source,Duel.Piece target){act(d,source.controller,"activate",source.token,target==null?"":target.token);resolve(d);}
    @Test void everyOfficialCardAndFormHasAnExactDefinitionAndDistinctPrimaryMechanics(){
        long pokemon=0;Set<String> species=new HashSet<>();
        for(var c:catalog.cards().values())if(c.category().equals("pokemon")&&!c.id().startsWith("special_")){
            assertNotNull(CardGameplayEffects.definition(c),"Uncovered card: "+c.id()+" / "+CardGameplayEffects.key(c));
            c.effect().spec().validate();assertEquals(0,c.effect().lifeCost());
            assertTrue(c.effect().spec().costs().stream().noneMatch(cost->cost.type().equals("LP_COST")),c.id());
            assertTrue(c.effect().spec().stages().stream().noneMatch(s->s.id().equals("signature")),c.id());
            if(c.aspects().isEmpty())species.add(c.species().replace("cobblemon:",""));pokemon++;
        }
        assertEquals(1025,species.size());assertTrue(pokemon>1025);CardIdentities.validateUniqueness(catalog);
        System.out.println("CARDWORLDS_GAMEPLAY_IDENTITIES species="+species.size()+" pokemonCards="+pokemon+" duplicatePrimary=0 duplicateMechanics=0 ordinalRecipes=0");
    }
    @Test void catalogInsertionsCannotChangeAnExistingCardsGameplay(){
        var before=CardIdentities.apply(catalog);var map=new LinkedHashMap<>(before.cards());var source=before.card("charmander");
        map.put("aaa_provider_fixture",new Catalog.Card("aaa_provider_fixture",source.name(),source.category(),"myaddon:fixture",source.aspects(),source.type(),source.family(),source.evolvesFrom(),source.extra(),source.level(),source.power(),source.text(),source.set(),source.rarity(),source.sources(),source.effect(),source.triggers(),source.modifiers()));
        var after=CardIdentities.apply(new Catalog(before.rules(),map,before.banners(),before.rewards(),before.dealers(),before.starters()));
        for(var c:before.cards().values())assertEquals(CardIdentities.mechanics(c.effect(),false),CardIdentities.mechanics(after.card(c.id()).effect(),false),c.id());
    }
    @Test void providerAuthoredAbilitiesArePreserved(){
        var original=catalog.card("charmander");var e=original.effect();var spec=e.spec();
        var ownStage=new EffectSpec.Stage("signature",catalog.card("onix").effect(),List.of("FIELD"),false);
        var custom=new Catalog.Effect(e.operation(),e.amount(),e.speed(),e.lifeCost(),e.target(),e.phases(),e.oncePerTurn(),new EffectSpec(spec.triggers(),spec.conditions(),spec.costs(),spec.targets(),spec.operations(),spec.oncePerDuel(),spec.optional(),spec.textKey(),spec.vfx(),List.of(ownStage),spec.limitScope(),spec.resolutionConditions()));
        var external=new Catalog.Card("addon_fixture",original.name(),original.category(),"myaddon:fixture",original.aspects(),original.type(),original.family(),original.evolvesFrom(),original.extra(),original.level(),original.power(),original.text(),original.set(),original.rarity(),original.sources(),custom,original.triggers(),original.modifiers());
        var cards=new LinkedHashMap<>(catalog.cards());cards.put(external.id(),external);
        var expanded=CardIdentities.apply(new Catalog(catalog.rules(),cards,catalog.banners(),catalog.rewards(),catalog.dealers(),catalog.starters()));
        assertEquals(CardIdentities.mechanics(custom,false),CardIdentities.mechanics(expanded.card(external.id()).effect(),false));
    }
    @Test void textAnimationCounterRenameAndNumbersCannotDisguiseADuplicate(){
        var c=catalog.card("charizard");var json=new Gson().toJsonTree(c.effect()).getAsJsonObject();
        json.getAsJsonObject("spec").addProperty("textKey","different prose");json.getAsJsonObject("spec").remove("vfx");
        var operations=json.getAsJsonObject("spec").getAsJsonArray("operations");operations.get(1).getAsJsonObject().addProperty("amount",7);operations.get(1).getAsJsonObject().getAsJsonObject("flags").addProperty("counter","renamed");
        json.getAsJsonObject("spec").getAsJsonArray("costs").get(0).getAsJsonObject().getAsJsonObject("filter").add("types",new Gson().toJsonTree(List.of("water")));
        var reversed=new JsonArray();for(int i=operations.size()-1;i>=0;i--)reversed.add(operations.get(i));json.getAsJsonObject("spec").add("operations",reversed);
        var duplicate=new Gson().fromJson(json,Catalog.Effect.class);
        assertEquals(CardIdentities.mechanics(c.effect(),true),CardIdentities.mechanics(duplicate,true));
    }
    @Test void blastoiseBuildsArmorAndHydroCannonPaysCountersAndChecksPosition(){
        var d=duel();var source=d.qaAdd(0,"blastoise",Duel.Zone.FIELD);var target=d.qaAdd(1,"squirtle",Duel.Zone.FIELD);
        use(d,source,null);assertEquals(1,source.counters.get("shell"));assertTrue(source.flags.contains("PREVENT_DAMAGE"));open(d);
        long rev=d.revision();assertThrows(IllegalArgumentException.class,()->act(d,0,"activate_stage",source.token,"hydro_cannon|"+target.token));assertEquals(rev,d.revision());assertEquals(1,source.counters.get("shell"));
        source.counters.put("shell",2);target.position=Duel.BattlePosition.DEFENSE;
        act(d,0,"activate_stage",source.token,"hydro_cannon|"+target.token);resolve(d);
        assertEquals(0,source.counters.get("shell"));assertEquals(Duel.Zone.DISCARD,target.zone);assertTrue(source.flags.contains("CANNOT_ATTACK"));assertEquals(Duel.Zone.FIELD,source.zone);
    }
    @Test void onixBindsInsteadOfBouncingDrawingOrHealing(){
        var d=duel();var source=d.qaAdd(0,"onix",Duel.Zone.FIELD);var target=d.qaAdd(1,"squirtle",Duel.Zone.FIELD);var reveal=d.qaAdd(0,"onix",Duel.Zone.HAND);
        int deck=d.view(0).deckCounts().getFirst();var life=d.view(0).life();use(d,source,target);
        assertEquals(Duel.Zone.FIELD,target.zone);assertEquals(Duel.BattlePosition.DEFENSE,target.position);assertTrue(target.flags.contains("CANNOT_ATTACK"));assertEquals(Duel.BattlePosition.DEFENSE,source.position);
        assertEquals(deck,d.view(0).deckCounts().getFirst());assertEquals(life,d.view(0).life());assertEquals(Duel.Zone.HAND,reveal.zone);
    }
    @Test void venusaurDrainsAtEndOfTurnOnlyWhileItsRememberedTargetSurvives(){
        for(boolean remove:List.of(false,true)){
            var d=duel();var source=d.qaAdd(0,"venusaur",Duel.Zone.FIELD);var target=d.qaAdd(1,"squirtle",Duel.Zone.FIELD);use(d,source,target);assertEquals(List.of(8000,8000),d.view(0).life());open(d);
            if(remove){var recall=d.qaAdd(0,"tidal_recall",Duel.Zone.HAND);use(d,recall,target);open(d);assertEquals(Duel.Zone.HAND,target.zone);}
            int guard=120;while(d.view(0).turn()==3&&guard-->0){if(d.view(0).open())act(d,d.view(0).turnPlayer(),"next","","");else act(d,d.priority(),"pass","","");}
            assertTrue(guard>0);assertFalse(d.view(0).chain().isEmpty(),"End-of-turn effect must survive the turn boundary");resolve(d);assertEquals(remove?8000:7700,d.view(0).life().get(1));assertEquals(remove?0:1,source.counters.getOrDefault("growth",0));
        }
    }
    @Test void allLearnsetBackedKitsResolveThroughTheRealActionPipeline(){
        var representatives=new TreeMap<String,String>();for(var c:catalog.cards().values()){
            var def=CardGameplayEffects.definition(c);if(def!=null&&"learnset-backed move adaptation".equals(def.authorship()))representatives.put(CardGameplayEffects.key(c),c.id());
        }
        Map<String,String> byType=new HashMap<>();catalog.cards().values().stream().filter(c->c.category().equals("pokemon")&&c.aspects().isEmpty()).sorted(Comparator.comparing(Catalog.Card::id)).forEach(c->byType.putIfAbsent(c.type(),c.id()));
        for(String id:representatives.values()){
            var d=duel();var source=d.qaAdd(0,id,Duel.Zone.FIELD);var target=d.qaAdd(1,"squirtle",Duel.Zone.FIELD);
            for(String payment:byType.values())d.qaAdd(0,payment,Duel.Zone.HAND);
            d.qaAdd(0,"charmander",Duel.Zone.DISCARD);d.qaAdd(0,"potion",Duel.Zone.DISCARD);
            long before=d.revision();assertDoesNotThrow(()->{act(d,0,"activate",source.token,source.card.effect().target().equals("none")?"":target.token);resolve(d);},id+" costs="+source.card.effect().spec().costs());
            assertTrue(d.revision()>before,id);assertTrue(d.view(0).log().stream().anyMatch(s->s.contains("resolves")),id);
        }
        assertTrue(representatives.size()>1300);
        System.out.println("CARDWORLDS_GAMEPLAY_EXECUTION kits="+representatives.size()+" realActions=true skipped=0");
    }
}
