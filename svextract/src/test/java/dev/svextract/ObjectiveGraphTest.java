package dev.svextract;

import dev.svextract.mission.ObjectiveGraph;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ObjectiveGraphTest {
    @Test void deadSignalRequiresPhysicalObjectiveOrder(){
        ObjectiveGraph graph=new ObjectiveGraph(ObjectiveGraph.deadSignal());
        assertFalse(graph.complete("data_core"));assertFalse(graph.complete("evacuation"));
        assertTrue(graph.complete("power"));assertFalse(graph.complete("power"));
        assertTrue(graph.complete("research_train"));assertTrue(graph.complete("data_core"));assertTrue(graph.complete("evacuation"));
        assertEquals("complete",graph.next());assertEquals(4,graph.completedIds().size());
    }
    @Test void invalidGraphCannotAdmitMission(){
        var a=new ObjectiveGraph.Node("a",ObjectiveGraph.Kind.REPAIR,List.of("b"),0,65,0);
        var b=new ObjectiveGraph.Node("b",ObjectiveGraph.Kind.RECOVERY,List.of("a"),1,65,0);
        assertThrows(IllegalArgumentException.class,()->new ObjectiveGraph(List.of(a,b)));
        assertThrows(IllegalArgumentException.class,()->new ObjectiveGraph(List.of(a)));
        assertThrows(IllegalArgumentException.class,()->new ObjectiveGraph(List.of(a,a)));
    }
    @Test void branchesSharePrerequisitesWithoutSharingCompletion(){
        var root=new ObjectiveGraph.Node("root",ObjectiveGraph.Kind.REPAIR,List.of(),0,65,0);
        var a=new ObjectiveGraph.Node("a",ObjectiveGraph.Kind.RECOVERY,List.of("root"),1,65,0);
        var b=new ObjectiveGraph.Node("b",ObjectiveGraph.Kind.INVESTIGATION,List.of("root"),2,65,0);
        ObjectiveGraph graph=new ObjectiveGraph(List.of(root,a,b));graph.complete("root");graph.complete("a");
        assertTrue(graph.available("b"));assertFalse(graph.completed("b"));
    }
}
