package vn.worldcomesalive;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.civilization.Lodging;
import vn.worldcomesalive.world.SettlementBootstrap;
import vn.worldcomesalive.data.WorldContent;
import vn.worldcomesalive.server.WorldStore;
import java.util.*;
class LodgingTest {
    @Test void roomsHaveStableExactBoundsAndDoNotConsumeResidentBeds(){var s=SettlementBootstrap.create(414212,new Pos(100,70,200),"plains",WorldContent.defaults(),"village");var rooms=Lodging.rooms(s);assertEquals(2,rooms.size());assertEquals(Lodging.rooms(s).stream().map(r->r.id).toList(),rooms.stream().map(r->r.id).toList());var inn=s.service("tavern");for(var r:rooms){assertTrue(r.contains(r.storage));for(Pos bed:r.beds){assertTrue(r.contains(bed));assertFalse(inn.markers.get(Marker.BED).contains(bed));}}}
    @Test void rentalsConserveMoneyAndRejectDoubleBookingOrForeignRenewal(){var r=new Lodging.Room();r.price=12;var p=new PlayerLife();var inn=new Building();UUID a=UUID.randomUUID(),b=UUID.randomUUID();assertTrue(Lodging.rent(r,a,p,inn,100));assertEquals(48,p.money);assertEquals(112,inn.money);assertFalse(Lodging.rent(r,b,new PlayerLife(),inn,101));assertFalse(Lodging.renew(r,b,p,inn,101));assertFalse(r.permitted(b,101));assertTrue(r.permitted(a,101));assertFalse(r.permitted(a,24100));assertTrue(Lodging.renew(r,a,p,inn,200));assertEquals(48100,r.until);assertEquals(36,p.money);}
    @Test void checkoutPreservesAllBelongingComponentsInRecovery(){var r=new Lodging.Room();r.state="RENTED";r.guest=UUID.randomUUID();r.contents.add("{\"id\":\"minecraft:diamond_sword\",\"components\":{\"minecraft:custom_name\":\"Keepsake\"}}");var p=new PlayerLife();Lodging.checkout(r,p,900);assertEquals(1,p.recovery.size());assertTrue(p.recovery.getFirst().contains("Keepsake"));assertTrue(r.contents.isEmpty());assertNull(r.guest);assertEquals("DIRTY",r.state);assertEquals(1300,r.cleanAt);}
    @Test void leaseDurationAndStorageSurviveSerialization(){var w=new LivingWorld();var r=new Lodging.Room();r.id=UUID.randomUUID();r.guest=UUID.randomUUID();r.until=88000;r.state="RENTED";r.contents.add("item-with-components");w.rooms.put(r.id,r);var loaded=WorldStore.JSON.fromJson(WorldStore.JSON.toJson(w),LivingWorld.class).rooms.get(r.id);assertEquals(r.guest,loaded.guest);assertEquals(r.until,loaded.until);assertEquals(r.contents,loaded.contents);}
}
