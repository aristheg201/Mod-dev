package vn.worldcomesalive;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.data.WorldContent;
import vn.worldcomesalive.ai.*;
import vn.worldcomesalive.world.SettlementBootstrap;
import vn.worldcomesalive.server.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LivingWorldTest {
    WorldContent data=WorldContent.defaults();
    LivingWorld world=new LivingWorld();
    Settlement settlement;
    @TempDir Path temp;
    @BeforeEach void setup(){settlement=SettlementBootstrap.create(414212,new Pos(100,70,200),"plains",data,"village");world.settlements.put(settlement.id,settlement);SettlementBootstrap.populate(world,settlement,data,Collections.nCopies(40,"pikachu"));}
    Npc npc(){return world.npcs.values().iterator().next();}
    @Test void deterministicBootstrap(){LivingWorld other=new LivingWorld();Settlement s=SettlementBootstrap.create(414212,new Pos(100,70,200),"plains",data,"village");other.settlements.put(s.id,s);SettlementBootstrap.populate(other,s,data,Collections.nCopies(40,"pikachu"));assertEquals(WorldStore.JSON.toJson(world),WorldStore.JSON.toJson(other));}
    @Test void populationNeverExceedsBedsAcrossSeeds(){for(long seed=0;seed<100;seed++){LivingWorld w=new LivingWorld();Settlement s=SettlementBootstrap.create(seed,new Pos(0,70,0),"forest",data,data.archetypes.get((int)(seed%data.archetypes.size())).id());SettlementBootstrap.populate(w,s,data,List.of("pikachu"));assertEquals(s.buildings.values().stream().mapToInt(b->b.beds).sum(),w.npcs.size());}}
    @Test void householdsAndDirectedFamilyRelations(){for(Npc n:world.npcs.values()){Household h=settlement.households.get(n.household);assertTrue(h.members.contains(n.id));assertEquals(h.home,n.home);for(UUID member:h.members)if(!member.equals(n.id))assertFalse(n.relationship(member).family.isEmpty());}}
    @Test void professionSlotsProduceWorkersAndPokemon(){for(String profession:List.of("baker","farmer","blacksmith","innkeeper","guard","healer")){Npc n=world.npcs.values().stream().filter(a->a.profession.equals(profession)).findFirst().orElseThrow();assertEquals(profession,settlement.buildings.get(n.workplace).profession);assertFalse(n.pokemon.isEmpty());}}
    @Test void generatedOwnershipCoversBuildingsBedsAndWorkstations(){for(Building b:settlement.buildings.values()){assertTrue(settlement.households.containsKey(UUID.fromString(b.owner)));assertEquals(b.beds,b.markers.get(Marker.BED).size());assertTrue(b.markers.containsKey(Marker.ENTRANCE));}}
    @Test void differentPeopleHaveDifferentPersonalities(){assertTrue(world.npcs.values().stream().map(n->n.personality.toString()).distinct().count()>10);}
    @Test void scheduleContributesWorkUtility(){Npc n=world.npcs.values().stream().filter(a->a.profession.equals("farmer")).findFirst().orElseThrow();n.needs.clear();assertEquals("worked",Cognition.decide(n,3000,data,true).goal());}
    @Test void urgentFireOverridesSleepAndWork(){Npc n=npc();n.interrupt="fire";n.interruptUntil=20000;n.needs.put("fatigue",1.0);var d=Cognition.decide(n,15000,data,true);assertEquals("safe",d.goal());assertEquals(List.of("flee"),d.plan());}
    @Test void plannerFindsAlternativeWhenBakeryClosed(){var plan=GoalPlanner.plan(Set.of("alive","can_gather"),"nourished",data.actions);assertEquals(List.of("gather_food","eat"),plan);}
    @Test void plannerBuysFoodBeforeEating(){assertEquals(List.of("visit_market","buy_food","eat"),GoalPlanner.plan(Set.of("alive","shop_open"),"nourished",data.actions));}
    @Test void importantMemorySurvivesConsolidation(){Npc n=npc();UUID actor=UUID.randomUUID();world.remember(n,new Memory("PLAYER_SAVED_MY_CHILD",actor,n.home,0,1,1,1,"witnessed"));for(int i=0;i<30;i++)world.remember(n,new Memory("friendly_greeting",actor,n.home,i,.1,.2,1,"witnessed"));assertTrue(n.memories.stream().anyMatch(m->m.type().equals("PLAYER_SAVED_MY_CHILD")));assertTrue(n.knowledge.values().stream().anyMatch(b->b.fact().equals("PLAYER_IS_FREQUENTLY_FRIENDLY")&&b.evidence()>=24));assertTrue(n.memories.size()<15);}
    @Test void persistenceRetainsRelationshipsMemoryDeckAndPartnerIdentity()throws Exception{Npc n=npc();UUID actor=UUID.randomUUID();n.relationship(actor).trust=63;n.relationship(actor).fear=14;world.remember(n,new Memory("helped",actor,n.home,10,.9,.5,1,"witnessed"));WorldStore store=new WorldStore(temp);store.save(world);LivingWorld loaded=store.load();assertEquals(WorldStore.JSON.toJson(world),WorldStore.JSON.toJson(loaded));assertEquals(63,loaded.npcs.get(n.id).relationship(actor).trust);}
    @Test void corruptionRecoversLastGoodBackup()throws Exception{WorldStore store=new WorldStore(temp);store.save(world);world.clock=12;store.save(world);Files.writeString(temp.resolve("world-comes-alive.json"),"{bad");assertEquals(0,store.load().clock);}
    @Test void corruptionWithoutBackupNeverResetsWorld()throws Exception{WorldStore store=new WorldStore(temp);store.save(world);Files.writeString(temp.resolve("world-comes-alive.json"),"{bad");assertThrows(java.io.IOException.class,store::load);}
    @Test void directionalRelationshipsAreNotForcedSymmetric(){Npc a=npc(),b=world.npcs.values().stream().skip(4).findFirst().orElseThrow();a.relationship(b.id).respect=80;assertEquals(0,b.relationship(a.id).respect);}
    @Test void travelInterpolatesRouteInsteadOfRandomSpawn(){Travel t=new Travel();t.departure=100;t.speed=.1;t.route=List.of(new Pos(0,70,0),new Pos(10,70,0),new Pos(10,70,10));assertEquals(new Pos(10,70,5),t.at(250));assertFalse(t.arrived(250));assertTrue(t.arrived(300));}
    @Test void fullToAbstractRebaseRetainsProgress(){Travel t=new Travel();t.route=new ArrayList<>(List.of(new Pos(0,70,0),new Pos(20,70,0)));t.speed=.1;t.departure=100;Pos actual=t.at(200);t.route.set(0,actual);t.departure=200;assertEquals(new Pos(15,70,0),t.at(250));}
    @Test void dialogueIsIndexedAndFiltersKnowledge(){Npc n=npc();UUID player=UUID.randomUUID();String text=SocialRules.dialogue(n,player,"Hero","talk",settlement,world,data,false);assertFalse(text.contains("remember the help"));assertTrue(data.candidates("talk","guard").stream().anyMatch(d->d.id().equals("guard")));assertTrue(data.candidates("gift","baker").stream().noneMatch(d->d.id().equals("guard")));}
    @Test void spamGiftsAreRejectedAndDoNotGuaranteeRomance(){Npc n=npc();UUID player=UUID.randomUUID();double first=SocialRules.gift(n,player,"minecraft:bread",Set.of("food"),world,data);assertTrue(first>0);for(int i=0;i<30;i++)assertTrue(SocialRules.gift(n,player,"minecraft:bread",Set.of("food"),world,data)<0);assertEquals(0,n.relationship(player).attraction);}
    @Test void cardAcceptanceDependsOnContextAndPersistentDeck(){Npc n=world.npcs.values().stream().filter(a->a.profession.equals("innkeeper")).findFirst().orElseThrow();UUID player=UUID.randomUUID();List<String> initial=List.copyOf(n.decks.get("casual"));assertTrue(SocialRules.acceptsCards(n,player,1,0));n.interruptUntil=100;assertFalse(SocialRules.acceptsCards(n,player,1,0));assertEquals(initial,n.decks.get("casual"));n.interruptUntil=0;n.relationship(player).trust=-30;assertFalse(SocialRules.acceptsCards(n,player,1,0));}
    @Test void authorityRejectsForgedStaleDistantAndExpiredRequests(){UUID id=UUID.randomUUID();assertTrue(Interactions.valid(id,id,1,1,2,2,100,200,4,true));assertFalse(Interactions.valid(id,id,3,1,2,2,100,200,4,true));assertFalse(Interactions.valid(id,id,1,1,1,2,100,200,4,true));assertFalse(Interactions.valid(id,id,1,1,2,2,201,200,4,true));assertFalse(Interactions.valid(id,id,1,1,2,2,100,200,8,true));assertFalse(Interactions.valid(id,id,1,1,2,2,100,200,4,false));}
    @Test void socializationProducesDirectedProgressAndGossip(){Npc a=npc(),b=world.npcs.values().stream().skip(5).findFirst().orElseThrow();world.clock=24000;UUID actor=UUID.randomUUID();world.remember(a,new Memory("helped",actor,a.home,100,.8,.5,1,"witnessed"));SocialRules.socialize(world,a,b);assertTrue(a.relationship(b.id).familiarity>0);assertTrue(b.knowledge.values().stream().anyMatch(k->k.source().startsWith("heard from")&&k.confidence()<1));}
    @Test void reloadingInvalidContentFailsBeforeMutation(){assertThrows(IllegalArgumentException.class,()->WorldContent.read(new java.io.StringReader("{}")));assertEquals(8,data.regions.size());}
}
