package vn.svarcade.tcg;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import vn.svarcade.tcg.data.Catalog;
import vn.svarcade.tcg.economy.CardStore;
import static org.junit.jupiter.api.Assertions.*;

class EconomyTest {
    @TempDir Path tmp;Catalog catalog;CardStore store;
    @BeforeEach void setup()throws Exception{catalog=Catalog.load(Path.of("missing.json"));store=new CardStore(tmp.resolve("cards.db"),catalog,new Random(7));store.createProfile("alice","crossroads");store.createProfile("bob","crossroads");}
    @AfterEach void close()throws Exception{store.close();}
    String first(String owner){return store.binder(owner,0).getFirst().serial();}
    @Test void starterCannotBeClaimedTwice(){assertThrows(IllegalArgumentException.class,()->store.createProfile("alice","crossroads"));assertEquals(40,store.profile("alice").owned());assertEquals(2000,store.profile("alice").coins());}
    @Test void marketPreservesFinderAndConservesCurrencyMinusTax(){String card=first("alice");String listing=store.list("alice",card,100);assertThrows(IllegalArgumentException.class,()->store.list("alice",card,100));store.buy("bob",listing);assertEquals(2095,store.profile("alice").coins());assertEquals(1900,store.profile("bob").coins());var acquired=java.util.stream.Stream.concat(store.binder("bob",0).stream(),store.binder("bob",1).stream()).filter(c->c.serial().equals(card)).findFirst().orElseThrow();assertEquals("alice",acquired.finder());assertEquals("bob",acquired.owner());assertThrows(IllegalArgumentException.class,()->store.buy("bob",listing));}
    @Test void twoBuyersCannotAcquireSameCard()throws Exception{store.createProfile("charlie","crossroads");String listing=store.list("alice",first("alice"),100);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->buy("bob",listing));var b=pool.submit(()->buy("charlie",listing));assertEquals(1,a.get()+b.get());}assertEquals(81,store.profile("bob").owned()+store.profile("charlie").owned());}
    int buy(String buyer,String listing){try{store.buy(buyer,listing);return 1;}catch(IllegalArgumentException ex){return 0;}}
    @Test void failedPurchaseRollsBackEverything(){String listing=store.list("alice",first("alice"),3000);assertThrows(IllegalArgumentException.class,()->store.buy("bob",listing));assertEquals(1,store.market(0).size());assertEquals(2000,store.profile("alice").coins());assertEquals(40,store.profile("bob").owned());}
    @Test void tradeEscrowAndCancellationRestoreAssets(){String a=first("alice"),b=first("bob");String offer=store.offer("alice","bob",a,b,300);assertEquals(1700,store.profile("alice").coins());assertThrows(IllegalArgumentException.class,()->store.list("alice",a,1));store.cancelOffer("bob",offer);assertEquals(2000,store.profile("alice").coins());store.list("alice",a,100);}
    @Test void acceptingTradeIsAtomicAndCannotRepeat(){String offer=store.offer("alice","bob",first("alice"),first("bob"),300);assertThrows(IllegalArgumentException.class,()->store.accept("alice",offer));store.accept("bob",offer);assertEquals(40,store.profile("alice").owned());assertEquals(2300,store.profile("bob").coins());assertThrows(IllegalArgumentException.class,()->store.accept("bob",offer));}
    @Test void missingRequestedCardDoesNotLoseEscrow(){String a=first("alice"),b=first("bob");String offer=store.offer("alice","bob",a,b,300);store.list("bob",b,100);assertThrows(IllegalArgumentException.class,()->store.accept("bob",offer));store.cancelOffer("alice",offer);assertEquals(2000,store.profile("alice").coins());}
    @Test void savedDeckIsRevalidatedWhenOwnershipChanges(){String id=store.list("alice",first("alice"),100);assertThrows(IllegalArgumentException.class,()->store.lockDeck("alice","Starter",false,"duel"));store.cancelListing("alice",id);assertEquals(40,store.lockDeck("alice","Starter",false,"duel").getFirst().size());assertThrows(IllegalArgumentException.class,()->store.list("alice",first("alice"),100));store.unlockDuel("duel");store.list("alice",first("alice"),100);}
    @Test void duplicatePhysicalCardCannotFillDeck(){var d=store.deck("alice","Starter");var bad=new ArrayList<>(d.main());bad.set(1,bad.getFirst());assertThrows(IllegalArgumentException.class,()->store.saveDeck("alice","Bad",bad,List.of(),false));}
    @Test void pullReplayReturnsSameResultWithoutChargingTwice(){var a=store.pull("alice","ghost_carnival","request-1",1);var b=store.pull("alice","ghost_carnival","request-1",1);assertEquals(a,b);assertEquals(1900,store.profile("alice").coins());assertEquals(41,store.profile("alice").owned());}
    @Test void worldRewardIsExclusiveAndOnlyGrantedOnce(){assertFalse(store.progress("alice","desert_discovery","minecraft:overworld","minecraft:plains"));assertTrue(store.progress("alice","desert_discovery","minecraft:overworld","minecraft:desert"));assertFalse(store.progress("alice","desert_discovery","minecraft:overworld","minecraft:desert"));assertEquals(41,store.profile("alice").owned());assertEquals(1,store.profile("alice").reputation());}
    @Test void persistenceSurvivesRestart()throws Exception{String listing=store.list("alice",first("alice"),100);store.close();store=new CardStore(tmp.resolve("cards.db"),catalog,new Random(9));assertEquals(listing,store.market(0).getFirst().id());store.buy("bob",listing);assertEquals(41,store.profile("bob").owned());}
    @Test void hardPityAndFeaturedGuaranteeUsePublishedOdds()throws Exception{
        var banner=new Catalog.Banner("Test","family","GACHA",1,1,2,2,0,0,0,List.of(new Catalog.Weighted("pikachu",100,false,false),new Catalog.Weighted("mega_charizard",1,false,true),new Catalog.Weighted("gengar",1,true,true)));
        var configured=new Catalog(catalog.rules(),catalog.cards(),Map.of("test",banner),catalog.rewards(),catalog.dealers(),catalog.starters());
        try(var s=new CardStore(tmp.resolve("pity.db"),configured,new Random(){@Override public long nextLong(long bound){return 0;}})){
            s.createProfile("alice","crossroads");assertEquals(100.0,s.probabilities("alice","test").values().stream().mapToDouble(Double::doubleValue).sum(),0.0001);
            assertEquals("pikachu",s.pull("alice","test","1",1).getFirst().card());
            assertFalse(s.probabilities("alice","test").containsKey("Pikachu"));
            assertEquals("mega_charizard",s.pull("alice","test","2",1).getFirst().card());assertTrue(s.pity("alice","family").guaranteed());
            s.pull("alice","test","3",1);assertEquals(Map.of("Gengar",100.0),s.probabilities("alice","test"));
            assertEquals("gengar",s.pull("alice","test","4",1).getFirst().card());assertFalse(s.pity("alice","family").guaranteed());
        }
    }
    @Test void limitedEditionCannotExceedSupply()throws Exception{
        var reward=new Catalog.Reward("Limited","ARCHAEOLOGY","","",1,"ancient_mew",1,0,0);
        var configured=new Catalog(catalog.rules(),catalog.cards(),catalog.banners(),Map.of("limited",reward),catalog.dealers(),catalog.starters());
        try(var s=new CardStore(tmp.resolve("supply.db"),configured,new Random(4))){s.createProfile("a","crossroads");s.createProfile("b","crossroads");assertTrue(s.progress("a","limited","",""));assertFalse(s.progress("b","limited","",""));assertEquals(41,s.profile("a").owned());assertEquals(40,s.profile("b").owned());}
    }
    @Test void npcRewardsHaveReceiptAndDailyLimit(){for(int i=0;i<7;i++)store.npcVictory("alice","pewter_victory","duel"+i,100);store.npcVictory("alice","pewter_victory","duel0",100);assertEquals(45,store.profile("alice").owned());assertEquals(2500,store.profile("alice").coins());}
    @Test void crashRecoveryReleasesDuelLocksButRetainsMarketEscrow()throws Exception{
        store.lockDeck("alice","Starter",false,"abandoned");String listed=first("bob");store.list("bob",listed,100);store.close();store=new CardStore(tmp.resolve("cards.db"),catalog,new Random(8));assertTrue(store.binder("alice",0).stream().allMatch(c->c.lock().isEmpty()));assertThrows(IllegalArgumentException.class,()->store.list("bob",listed,100));
    }
    @Test void collectionSurfacesEveryUniqueIdentityBeforeDuplicates(){var cards=store.collection("alice");int unique=store.completion("alice").size();assertEquals(unique,cards.subList(0,unique).stream().map(CardStore.Owned::card).distinct().count());assertEquals(40,cards.size());}
    @Test void deckFormatPersistsWithoutApplyingRankedBansToOtherFormats()throws Exception{
        var starter=new ArrayList<>(catalog.starters().get("crossroads"));starter.set(0,"shadow_lugia");
        var custom=new Catalog(catalog.rules(),catalog.cards(),catalog.banners(),catalog.rewards(),catalog.dealers(),Map.of("custom",starter));
        try(var s=new CardStore(tmp.resolve("formats.db"),custom,new Random(1))){s.createProfile("collector","custom");var deck=s.deck("collector","Starter");for(var f:CardStore.Format.values()){if(f==CardStore.Format.RANKED){assertThrows(IllegalArgumentException.class,()->s.saveDeck("collector","Shadow",deck.main(),deck.extra(),f));}else{s.saveDeck("collector","Shadow",deck.main(),deck.extra(),f);assertEquals(f.name(),s.formats("collector").get("Shadow"));}}assertEquals("UNDERGROUND",s.formats("collector").get("Shadow"));}
    }
    @Test void adminGrantCommandsPersistCardsCoinsAndDecks(){
        store.createProfile("admin-target","crossroads");
        long before=store.profile("admin-target").coins();
        int cardsBefore=store.profile("admin-target").owned();
        store.adminGrantCard("admin-target","pikachu",2,"Holo");
        store.adminGrantCoins("admin-target",250);
        store.adminGrantDeck("admin-target","crossroads","Granted");
        assertTrue(store.profile("admin-target").coins()>=before+250);
        assertTrue(store.profile("admin-target").owned()>cardsBefore+1);
        assertTrue(store.deckNames("admin-target").contains("Granted"));
        assertTrue(store.completion("admin-target").getOrDefault("pikachu",0)>=2);
    }

}
