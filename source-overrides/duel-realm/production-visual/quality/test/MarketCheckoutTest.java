package vn.svarcade.tcg.economy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.svarcade.tcg.data.Catalog;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Provider fake + real SQLite: never treat legacy profile credits as the purchase balance. */
class MarketCheckoutTest {
 @TempDir Path dir;CardStore store;Catalog catalog;Wallet wallet;
 static final class Wallet implements MarketCheckout.Wallet {
  final Map<String,Long> balance=new HashMap<>(Map.of("alice",0L,"bob",1000L,"eve",1000L));int debits,credits;boolean failDebit,failCredit;
  public boolean debit(String owner,long amount,String currency){assertEquals(CardWorldsCurrency.BEAST,currency);debits++;long before=balance.get(owner);if(before<amount)return false;balance.put(owner,before-amount);if(failDebit)throw new IllegalStateException("Uncertain provider debit");return true;}
  public void credit(String owner,long amount,String currency){assertEquals(CardWorldsCurrency.BEAST,currency);credits++;balance.merge(owner,amount,Long::sum);if(failCredit)throw new IllegalStateException("Uncertain provider credit");}
 }
 @BeforeEach void setup()throws Exception{catalog=Catalog.load(dir.resolve("absent.json"));store=new CardStore(dir.resolve("cards.db"),catalog,new Random(42));for(String owner:List.of("alice","bob","eve"))store.createProfile(owner,"crossroads");wallet=new Wallet();}
 @AfterEach void close()throws Exception{store.close();}
 String serial(){return store.binder("alice",0).getFirst().serial();}
 @Test void providerCheckoutPaysSellerTaxAndReplayDoesNotTouchLegacyCredits(){
  String serial=serial(),listing=store.listEconomy("alice",serial,100);MarketCheckout.buy(store,wallet,"bob",listing,"request");MarketCheckout.buy(store,wallet,"bob",listing,"request");
  assertEquals(1,wallet.debits);assertEquals(1,wallet.credits);assertEquals(900,wallet.balance.get("bob"));assertEquals(95,wallet.balance.get("alice"));assertEquals(2000,store.profile("bob").coins());assertEquals(2000,store.profile("alice").coins());assertEquals(41,store.profile("bob").owned());assertEquals(39,store.profile("alice").owned());assertTrue(store.market(0).isEmpty());
 }
 @Test void concurrentSameReceiptPaysAndTransfersExactlyOnce()throws Exception{
  String listing=store.listEconomy("alice",serial(),100);var executor=Executors.newFixedThreadPool(2);
  try{var first=executor.submit(()->MarketCheckout.buy(store,wallet,"bob",listing,"same"));var second=executor.submit(()->MarketCheckout.buy(store,wallet,"bob",listing,"same"));first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);}finally{executor.shutdownNow();}
  assertEquals(1,wallet.debits);assertEquals(1,wallet.credits);assertEquals(41,store.profile("bob").owned());
 }
 @Test void differentReceiptsCannotBuyTheSameEscrowTwice()throws Exception{
  String listing=store.listEconomy("alice",serial(),100);var executor=Executors.newFixedThreadPool(2);
  try{var first=executor.submit(()->{try{MarketCheckout.buy(store,wallet,"bob",listing,"first");return true;}catch(IllegalArgumentException e){return false;}});var second=executor.submit(()->{try{MarketCheckout.buy(store,wallet,"eve",listing,"second");return true;}catch(IllegalArgumentException e){return false;}});assertNotEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));}finally{executor.shutdownNow();}
  assertEquals(1,wallet.debits);assertEquals(1,wallet.credits);assertEquals(81,store.profile("bob").owned()+store.profile("eve").owned());
 }
 @Test void completedReceiptSurvivesRestart()throws Exception{
  String listing=store.listEconomy("alice",serial(),100);MarketCheckout.buy(store,wallet,"bob",listing,"restart");store.close();store=new CardStore(dir.resolve("cards.db"),catalog,new Random(43));MarketCheckout.buy(store,wallet,"bob",listing,"restart");assertEquals(1,wallet.debits);assertEquals(1,wallet.credits);assertEquals(41,store.profile("bob").owned());
 }
 @Test void nativeCreditsCannotFundAProviderPurchase(){
  String listing=store.listEconomy("alice",serial(),100);wallet.balance.put("bob",0L);assertThrows(IllegalArgumentException.class,()->MarketCheckout.buy(store,wallet,"bob",listing,"empty"));assertEquals(40,store.profile("bob").owned());assertEquals(2000,store.profile("bob").coins());assertEquals(0,wallet.credits);store.cancelListing("alice",listing);
 }
 @Test void uncertainDebitIsDurableAndNeverRepeatedAfterRestart()throws Exception{
  String listing=store.listEconomy("alice",serial(),100);wallet.failDebit=true;assertThrows(IllegalStateException.class,()->MarketCheckout.buy(store,wallet,"bob",listing,"uncertain"));store.close();store=new CardStore(dir.resolve("cards.db"),catalog,new Random(43));wallet.failDebit=false;
  assertThrows(IllegalStateException.class,()->MarketCheckout.buy(store,wallet,"bob",listing,"uncertain"));assertEquals(1,wallet.debits);assertEquals(900,wallet.balance.get("bob"));assertEquals(0,wallet.credits);assertThrows(IllegalArgumentException.class,()->store.cancelListing("alice",listing));
 }
 @Test void uncertainSellerCreditIsNotRepeatedOrRefundedBlindly(){
  String listing=store.listEconomy("alice",serial(),100);wallet.failCredit=true;assertThrows(IllegalStateException.class,()->MarketCheckout.buy(store,wallet,"bob",listing,"credit"));wallet.failCredit=false;assertThrows(IllegalStateException.class,()->MarketCheckout.buy(store,wallet,"bob",listing,"credit"));assertEquals(1,wallet.debits);assertEquals(1,wallet.credits);assertEquals(900,wallet.balance.get("bob"));assertEquals(95,wallet.balance.get("alice"));assertEquals(40,store.profile("bob").owned());
 }
 @Test void legacyPricesRequireSellerRelistingAndNativeApiCannotSpendProviderListing(){
  String serial=serial(),legacy=store.list("alice",serial,100);assertThrows(IllegalArgumentException.class,()->MarketCheckout.buy(store,wallet,"bob",legacy,"legacy"));assertEquals(0,wallet.debits);store.cancelListing("alice",legacy);String listing=store.listEconomy("alice",serial,100);assertEquals(CardWorldsCurrency.BEAST,store.market(0).getFirst().currency());assertThrows(IllegalArgumentException.class,()->store.buy("bob",listing));assertEquals(2000,store.profile("bob").coins());
 }
 @Test void receiptCannotBeReusedByAnotherBuyerOrListing(){
  String first=store.listEconomy("alice",serial(),100);MarketCheckout.buy(store,wallet,"bob",first,"owned");assertThrows(IllegalArgumentException.class,()->MarketCheckout.buy(store,wallet,"eve",first,"owned"));String second=store.listEconomy("alice",serial(),100);assertThrows(IllegalArgumentException.class,()->MarketCheckout.buy(store,wallet,"bob",second,"owned"));assertEquals(1,wallet.debits);
 }
}
