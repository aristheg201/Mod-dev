package dev.svextract;

import dev.svextract.mission.ExtractorBotBrain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtractorBotBrainTest {
    static ExtractorBotBrain.Context ctx(boolean enemy,int bullets,boolean near,int value,int remaining){
        return new ExtractorBotBrain.Context(.9,bullets,30,enemy,false,true,true,value,remaining,true,near,false);
    }
    @Test void fightsReloadsAndEventuallyExtracts(){
        var brain=new ExtractorBotBrain(10,100);
        assertEquals(ExtractorBotBrain.Goal.ENGAGE,brain.decide(ctx(true,6,false,0,600),0).goal());
        assertEquals(ExtractorBotBrain.Goal.ENGAGE,brain.decide(ctx(true,0,false,0,600),5).goal());
        assertEquals(ExtractorBotBrain.Goal.RELOAD,brain.decide(ctx(true,0,false,0,600),10).goal());
        assertEquals(ExtractorBotBrain.Goal.EXTRACT,brain.decide(ctx(false,5,false,150,400),20).goal());
        assertEquals(ExtractorBotBrain.Goal.EVACUATED,brain.decide(ctx(false,5,true,150,380),30).goal());
        assertTrue(brain.evacuated());
    }
}
