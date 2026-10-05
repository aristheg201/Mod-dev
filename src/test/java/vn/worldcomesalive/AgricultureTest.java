package vn.worldcomesalive;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.agriculture.Agriculture;
import vn.worldcomesalive.world.SettlementBootstrap;
import vn.worldcomesalive.data.WorldContent;
import vn.worldcomesalive.server.WorldStore;
import java.util.*;
class AgricultureTest {
    private LivingWorld world(long seed){var w=new LivingWorld();var d=WorldContent.defaults();var s=SettlementBootstrap.create(seed,new Pos(0,70,0),"plains",d,"village");w.settlements.put(s.id,s);SettlementBootstrap.populate(w,s,d,List.of());return w;}
    @Test void farmingCapacityComesFromActualPlotsAndAssignedWorkers(){var w=world(414212);var s=w.settlements.values().iterator().next();assertTrue(s.fields.values().stream().filter(p->p.width>=12).mapToInt(p->p.capacity).sum()>=s.residents.size()*8);assertNotNull(s.service("barn"));assertFalse(s.pastures.isEmpty());for(var p:s.fields.values()){assertEquals(p.capacity,p.cells.size());assertTrue(s.households.containsKey(UUID.fromString(p.owner)));assertTrue(s.buildings.containsKey(p.storage));assertFalse(p.workers.isEmpty());for(UUID worker:p.workers)assertEquals("farmer",w.npcs.get(worker).profession);}}
    @Test void aHarvestCreditsActualStorageOnceAndChangesCropState(){var w=world(414212);var s=w.settlements.values().iterator().next();var p=s.fields.values().stream().filter(a->a.width>=12).findFirst().orElseThrow();p.state="MATURE";var storage=s.buildings.get(p.storage);int before=storage.stock.getOrDefault(p.crop,0);int yield=Agriculture.harvest(p,storage);assertTrue(yield>0);assertEquals(before+yield,storage.stock.get(p.crop));assertEquals("HARVESTED",p.state);assertEquals(0,Agriculture.harvest(p,storage));assertEquals(before+yield,storage.stock.get(p.crop));}
    @Test void farmerPrioritizesHisRealMatureField(){var w=world(414212);var s=w.settlements.values().iterator().next();var farmer=w.npcs.values().stream().filter(n->n.profession.equals("farmer")).findFirst().orElseThrow();var mature=s.fields.values().stream().filter(p->p.width>=12).skip(1).findFirst().orElseThrow();s.fields.values().forEach(p->p.state="GROWING");mature.state="MATURE";assertEquals(mature,Agriculture.workPlot(s,farmer));var stranger=new Npc();stranger.id=UUID.randomUUID();assertNull(Agriculture.workPlot(s,stranger));}
    @Test void plotOwnershipAndCropProgressSurviveSave(){var w=world(414212);var s=w.settlements.values().iterator().next();var p=s.fields.values().iterator().next();p.state="HARVESTED";p.due=9000;var restored=WorldStore.JSON.fromJson(WorldStore.JSON.toJson(w),LivingWorld.class);var q=restored.settlements.get(s.id).fields.get(p.id);assertEquals(p.owner,q.owner);assertEquals(p.workers,q.workers);assertEquals(p.cells,q.cells);assertEquals("HARVESTED",q.state);assertEquals(9000,q.due);assertEquals(s.livestock.keySet(),restored.settlements.get(s.id).livestock.keySet());}
}
