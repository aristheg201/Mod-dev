package vn.svarcade.tcg;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import vn.svarcade.tcg.data.Catalog;
import vn.svarcade.tcg.duel.Duel;
import vn.svarcade.tcg.duel.SummonFramework;
import vn.svarcade.tcg.duel.SpellTrapRules;
import static org.junit.jupiter.api.Assertions.*;

class EngineTest {
    Catalog base;
    @BeforeEach void load()throws Exception{base=Catalog.load(Path.of("nonexistent-test-catalog.json"));}
    Catalog small(){var r=base.rules();return new Catalog(new Catalog.Rules(5,60,15,3,5,8000,5,5,1,300,r.rankedLimits(),r.effective()),base.cards(),base.banners(),base.rewards(),base.dealers(),base.starters());}
    Duel duel(){return new Duel(small(),List.of("flamethrower","feint","charmander","protect","exit"),List.of("mega_charizard"),List.of("protect","feint","squirtle","potion","research"),List.of(),new Random(17));}
    void act(Duel d,int p,String action,String card,String target){d.act(p,new Duel.Action(action,card,target),d.revision());}
    void main(Duel d){for(int i=0;i<2;i++){act(d,0,"next","","");act(d,1,"pass","","");}}
    String token(Duel d,int seat,String name){return d.view(seat).cards().stream().filter(c->c.controller()==seat&&c.name().equals(name)).findFirst().orElseThrow().token();}
    @Test void hiddenInformationIsRedacted(){Duel d=duel();assertEquals(5,d.view(0).handCounts().get(1));assertTrue(d.view(0).cards().stream().noneMatch(c->c.controller()==1));assertTrue(d.view(-1).cards().isEmpty());}
    @Test void chainResolvesBackwardsAndNegatedCostsStayPaid(){Duel d=duel();main(d);
        act(d,0,"activate",token(d,0,"Flamethrower"),"");
        act(d,1,"activate",token(d,1,"Protect"),"1");
        act(d,0,"activate",token(d,0,"Feint"),"2");
        act(d,1,"pass","","");act(d,0,"pass","","");
        assertEquals(List.of(7500,7200),d.view(0).life());
        assertTrue(d.view(0).chain().isEmpty());assertFalse(d.view(0).open());
        assertTrue(d.view(0).log().indexOf("Feint resolves.")<d.view(0).log().indexOf("Flamethrower resolves."));
    }
    @Test void invalidResponseDoesNotPayCost(){Duel d=duel();main(d);act(d,0,"activate",token(d,0,"Flamethrower"),"");act(d,1,"activate",token(d,1,"Feint"),"1");long rev=d.revision();var life=d.view(0).life();assertThrows(IllegalArgumentException.class,()->act(d,0,"activate",token(d,0,"Protect"),"2"));assertEquals(rev,d.revision());assertEquals(life,d.view(0).life());}
    @Test void staleAndWrongPriorityActionsAreRejected(){Duel d=duel();assertThrows(IllegalArgumentException.class,()->act(d,1,"pass","",""));long old=d.revision();act(d,0,"next","","");assertThrows(IllegalArgumentException.class,()->d.act(1,new Duel.Action("pass","",""),old));}
    @Test void normalSummonNeedsOpenMainAndResponsePasses(){Duel d=duel();String c=token(d,0,"Charmander");assertThrows(IllegalArgumentException.class,()->act(d,0,"play",c,""));main(d);act(d,0,"play",c,"");assertFalse(d.view(0).open());assertEquals(0,d.view(0).priority());act(d,0,"pass","","");act(d,1,"pass","","");assertTrue(d.view(0).open());assertEquals(Duel.Cause.PLAY,d.history().getLast().cause());}
    @Test void evolutionAndExtraDeckPreserveBoardCapacity(){
        Duel d=new Duel(small(),List.of("charmander","charmeleon","charizard","potion","protect"),List.of("mega_charizard"),List.of("squirtle","wartortle","blastoise","potion","protect"),List.of(),new Random(3));main(d);
        String previous=token(d,0,"Charmander");act(d,0,"play",previous,"");act(d,0,"pass","","");act(d,1,"pass","","");
        for(String next:List.of("Charmeleon","Charizard","Mega Charizard X")){String t=token(d,0,next);act(d,0,"play",t,previous);act(d,0,"pass","","");act(d,1,"pass","","");previous=t;}
        assertEquals(1,d.view(0).cards().stream().filter(c->c.zone()==Duel.Zone.FIELD).count());assertEquals(Duel.Cause.EXTRA_SUMMON,d.history().getLast().cause());
    }
    @Test void rankedBansAndExtraPlacementAreDataDriven(){var main=new ArrayList<>(base.starters().get("crossroads"));main.set(0,"shadow_lugia");assertTrue(base.deckErrors(main,List.of(),true).stream().anyMatch(x->x.contains("Shadow Lugia")));assertTrue(base.deckErrors(main,List.of(),false).isEmpty());main.set(0,"mega_charizard");assertFalse(base.deckErrors(main,List.of(),false).isEmpty());}
    @Test void illegalWorldCardInPackFailsCatalogValidation(){var b=base.banners().get("crossroads");var broken=new Catalog.Banner(b.name(),b.family(),b.source(),b.price(),b.slots(),b.hardPity(),b.softPity(),b.softBonus(),0,0,List.of(new Catalog.Weighted("ancient_mew",1,true,true)));var bad=new Catalog(base.rules(),base.cards(),Map.of("broken",broken),base.rewards(),base.dealers(),base.starters());assertThrows(IllegalArgumentException.class,bad::validate);}
    @Test void concessionEndsDuelAndRejectsFurtherInput(){Duel d=duel();act(d,1,"concede","","");assertEquals(0,d.winner());assertThrows(IllegalArgumentException.class,()->act(d,0,"pass","",""));}
    @Test void bothPassesDoNotSkipPostChainResponseWindow(){Duel d=duel();main(d);act(d,0,"activate",token(d,0,"Flamethrower"),"");act(d,1,"pass","","");assertEquals(1,d.view(0).chain().size());act(d,0,"pass","","");assertEquals("MAIN1",d.view(0).phase());assertFalse(d.view(0).open());act(d,0,"pass","","");act(d,1,"pass","","");assertTrue(d.view(0).open());assertEquals("MAIN1",d.view(0).phase());}
    @Test void deckExhaustionProducesWinner(){Duel d=duel();for(int step=0;step<6;step++){act(d,0,"next","","");act(d,1,"pass","","");}assertEquals(0,d.winner());}
    @Test void mandatorySummonTriggerAllowsOpponentResponse(){Duel d=new Duel(small(),List.of("eevee","charmander","potion","protect","research"),List.of(),List.of("squirtle","wartortle","blastoise","potion","protect"),List.of(),new Random(2));main(d);act(d,0,"play",token(d,0,"Eevee"),"");assertEquals(1,d.view(0).chain().size());assertEquals(1,d.view(0).priority());act(d,1,"activate",token(d,1,"Protect"),"1");act(d,0,"pass","","");act(d,1,"pass","","");assertEquals(8000,d.view(0).life().getFirst());}
    @Test void continuousStadiumBonusSurvivesEffectNegation(){Duel d=new Duel(small(),List.of("training_ground","charmander","potion","protect","research"),List.of(),List.of("squirtle","wartortle","blastoise","potion","protect"),List.of(),new Random(2));main(d);String ally=token(d,0,"Charmander");act(d,0,"play",ally,"");act(d,0,"pass","","");act(d,1,"pass","","");act(d,0,"activate",token(d,0,"Training Ground"),ally);act(d,1,"activate",token(d,1,"Protect"),"1");act(d,0,"pass","","");act(d,1,"pass","","");assertEquals(1100,d.view(0).cards().stream().filter(c->c.token().equals(ally)).findFirst().orElseThrow().power());assertEquals(Duel.Zone.STADIUM,d.view(0).cards().stream().filter(c->c.name().equals("Training Ground")).findFirst().orElseThrow().zone());}
    Catalog multiNormal(){var r=base.rules();return new Catalog(new Catalog.Rules(5,60,15,3,5,8000,5,5,4,300,r.rankedLimits(),r.effective()),base.cards(),base.banners(),base.rewards(),base.dealers(),base.starters());}
    @Test void levelsOneToFourNeedNoTributeAndFiveToSixNeedOne(){
        Duel d=new Duel(multiNormal(),List.of("charmander","onix","potion","protect","research"),List.of(),List.of("squirtle","wartortle","blastoise","potion","protect"),List.of(),new Random(1));main(d);
        String low=token(d,0,"Charmander");act(d,0,"play",low,"");act(d,0,"pass","","");act(d,1,"pass","","");
        String high=token(d,0,"Onix");assertThrows(IllegalArgumentException.class,()->act(d,0,"play",high,""));
        act(d,0,"play",high,low);assertEquals(Duel.Cause.TRIBUTE_SUMMON,d.history().getLast().cause());
        assertTrue(d.view(0).cards().stream().anyMatch(c->c.name().equals("Charmander")&&c.zone()==Duel.Zone.DISCARD));
    }
    @Test void levelSevenPlusNeedsTwoTributes(){
        Duel d=new Duel(multiNormal(),List.of("charmander","squirtle","ancient_mew","protect","research"),List.of(),List.of("pikachu","gastly","potion","protect","research"),List.of(),new Random(1));main(d);
        String a=token(d,0,"Charmander"),b=token(d,0,"Squirtle"),boss=token(d,0,"Ancient Mew");
        act(d,0,"play",a,"");act(d,0,"pass","","");act(d,1,"pass","","");
        act(d,0,"play",b,"");act(d,0,"pass","","");act(d,1,"pass","","");
        assertThrows(IllegalArgumentException.class,()->act(d,0,"play",boss,a));
        act(d,0,"play",boss,a+","+b);assertEquals(Duel.Cause.TRIBUTE_SUMMON,d.history().getLast().cause());
    }

    @Test void spectatorViewProtectsHandsAndFaceDownIdentity(){
        Duel d=new Duel(small(),List.of("mirror_barrier","charmander","potion","protect","research"),List.of(),List.of("squirtle","wartortle","blastoise","potion","protect"),List.of(),new Random(2));main(d);
        String trap=token(d,0,"Mirror Barrier");act(d,0,"play",trap,"SET");
        assertEquals("Mirror Barrier",d.view(0).cards().stream().filter(c->c.token().equals(trap)).findFirst().orElseThrow().name());
        assertTrue(d.spectatorView().cards().stream().anyMatch(c->c.token().equals(trap)&&c.name().equals("Set card")&&c.category().equals("facedown")));
        assertEquals(4,d.spectatorView().handCounts().getFirst());
        assertTrue(d.spectatorView().cards().stream().noneMatch(c->c.zone()==Duel.Zone.HAND||c.zone()==Duel.Zone.EXTRA));
    }
    @Test void trapCannotActivateOnTheTurnItWasSet(){
        Duel d=new Duel(small(),List.of("mirror_barrier","charmander","potion","protect","research"),List.of(),List.of("squirtle","wartortle","blastoise","potion","protect"),List.of(),new Random(2));main(d);
        String trap=token(d,0,"Mirror Barrier");act(d,0,"play",trap,"SET");act(d,0,"pass","","");act(d,1,"pass","","");
        assertThrows(IllegalArgumentException.class,()->act(d,0,"activate",trap,""));
    }
    @Test void advancedSummonProfilesAreDataDriven(){
        var profile=SummonFramework.byId("creation_arceus");assertNotNull(profile);assertEquals(3,profile.materialCount());assertEquals("arceus_defense",profile.stages().getFirst().card());assertEquals(2,profile.stages().getFirst().lockTurns());assertEquals("ultimate_arceus",profile.stages().getLast().card());
        assertEquals(SpellTrapRules.Kind.COUNTER_TRAP,SpellTrapRules.profile(base.card("counter_seal")).kind());assertFalse(SpellTrapRules.profile(base.card("counter_seal")).activateFromHand());
    }
    @Test void qaSpellTrapScenarioRunsThreeLinkChainAndResolves(){
        Duel d=duel();d.qaPrepareSpellTrapScenario(0);
        assertTrue(d.spectatorView().cards().stream().filter(c->c.zone()==Duel.Zone.SUPPORT).allMatch(c->c.name().equals("Set card")));
        d.qaOpenSpellTrapChain(0);assertEquals(3,d.view(0).chain().size());
        d.qaResolveSpellTrapChain(0);assertTrue(d.view(0).chain().isEmpty());
        assertEquals(List.of(7500,7200),d.view(0).life());
        assertTrue(d.view(0).log().contains("Flamethrower resolves."));
        assertTrue(d.view(0).cards().stream().filter(c->c.name().equals("Counter Seal")||c.name().equals("Mirror Barrier")).allMatch(c->c.zone()==Duel.Zone.DISCARD));
    }
    @Test void qaCreationScenarioReachesUltimateArceus(){
        Duel d=duel();d.qaPrepareCreationScenario(0);
        assertEquals(3,d.view(0).cards().stream().filter(c->c.controller()==0&&c.zone()==Duel.Zone.FIELD&&Set.of("Palkia","Dialga","Giratina").contains(c.name())).count());
        d.qaSummonCreation(0);
        assertTrue(d.view(0).cards().stream().anyMatch(c->c.name().equals("Arceus - Defense")&&c.zone()==Duel.Zone.FIELD));
        d.qaTickCreation(0);assertTrue(d.view(0).cards().stream().anyMatch(c->c.name().equals("Arceus - Defense")&&c.zone()==Duel.Zone.FIELD));
        d.qaTickCreation(0);assertTrue(d.view(0).cards().stream().anyMatch(c->c.name().equals("Arceus - Judgement")&&c.zone()==Duel.Zone.FIELD));
        d.qaTickCreation(0);assertTrue(d.view(0).cards().stream().anyMatch(c->c.name().equals("Ultimate Arceus")&&c.zone()==Duel.Zone.FIELD));
        assertEquals(3,d.view(0).cards().stream().filter(c->c.controller()==0&&c.zone()==Duel.Zone.DISCARD&&Set.of("Palkia","Dialga","Giratina").contains(c.name())).count());
    }
    @Test void setQuickPlaySpellCannotActivateUntilNextTurn(){
        Duel d=duel();main(d);String protect=token(d,0,"Protect");act(d,0,"play",protect,"SET");act(d,0,"pass","","");act(d,1,"pass","","");
        act(d,0,"activate",token(d,0,"Flamethrower"),"");act(d,1,"pass","","");
        assertThrows(IllegalArgumentException.class,()->act(d,0,"activate",protect,"1"));
    }
    @Test void monsterPositionsUseCardStatesAndHideFaceDownIdentity(){
        Duel d=duel();d.qaPreparePositionScenario(0);
        assertTrue(d.view(0).cards().stream().anyMatch(c->c.zone()==Duel.Zone.FIELD&&c.position().equals("ATTACK")));
        assertTrue(d.view(0).cards().stream().anyMatch(c->c.name().equals("Pikachu")&&c.position().equals("DEFENSE")));
        assertTrue(d.spectatorView().cards().stream().anyMatch(c->c.name().equals("Set Pokemon")&&c.category().equals("facedown_pokemon")&&c.position().equals("FACE_DOWN_DEFENSE")));
    }
    @Test void setMonsterCreatesFaceDownDefensePosition(){
        Duel d=duel();main(d);String monster=token(d,0,"Charmander");act(d,0,"set_monster",monster,"");
        var own=d.view(0).cards().stream().filter(c->c.token().equals(monster)).findFirst().orElseThrow();
        assertEquals("FACE_DOWN_DEFENSE",own.position());assertEquals("Charmander",own.name());
        var hidden=d.spectatorView().cards().stream().filter(c->c.token().equals(monster)).findFirst().orElseThrow();
        assertEquals("Set Pokemon",hidden.name());assertEquals("facedown_pokemon",hidden.category());
    }

}
