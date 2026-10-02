package vn.svarcade.tcg.duel;
import org.junit.jupiter.api.Test;
import vn.svarcade.tcg.data.*;
import vn.svarcade.tcg.fabric.HeadlessCatalog;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** More than 64 scheduled effects must all resolve, in registration order, only on their actual event. */
class DelayedBucketTest {
 @Test void allScheduledEffectsResolveWithoutPollingOrDropping()throws Exception{
  var c=HeadlessCatalog.load();var d=DeterminismTest.fixture(c,7);var source=((Map<String,Duel.Piece>)DeterminismTest.field(d,"pieces")).values().stream().filter(p->p.controller==0&&p.zone==Duel.Zone.FIELD).findFirst().orElseThrow();
  var schedule=Duel.class.getDeclaredMethod("schedule",Duel.Link.class,EffectSpec.Operation.class);schedule.setAccessible(true);var triggers=Duel.class.getDeclaredMethod("collectCompositeTriggers",List.class);triggers.setAccessible(true);var resolve=Duel.class.getDeclaredMethod("resolve");resolve.setAccessible(true);
  var heal=new EffectSpec.Operation("HEAL_LP",1,"ALLY_LP",null,null,Map.of(),List.of(),null,List.of());var op=new EffectSpec.Operation("DELAY",0,null,null,"NEXT_TURN_END",Map.of("trigger","ON_TURN_END"),List.of(heal),null,List.of());
  for(int i=0;i<72;i++)schedule.invoke(d,new Duel.Link(source,"",0,i+1),op);
  var buckets=(Map<String,List<?>>)DeterminismTest.field(d,"delayed");assertEquals(72,buckets.get("ON_TURN_END").size());int before=d.view(0).life().get(0);
  triggers.invoke(d,List.of(new Duel.Event(1,"unrelated","",0,0,null,null,Duel.Cause.SYSTEM,"",0)));assertEquals(72,buckets.get("ON_TURN_END").size());assertTrue(d.view(0).chain().isEmpty());
  triggers.invoke(d,List.of(new Duel.Event(2,"ON_TURN_END","",0,0,null,null,Duel.Cause.SYSTEM,"",0)));assertEquals(72,d.view(0).chain().size());resolve.invoke(d);assertEquals(before+72,d.view(0).life().get(0));assertTrue(buckets.get("ON_TURN_END").isEmpty());
 }
}
