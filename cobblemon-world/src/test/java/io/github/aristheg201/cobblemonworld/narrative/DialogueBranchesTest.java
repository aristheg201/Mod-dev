package io.github.aristheg201.cobblemonworld.narrative;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/** Validate gameplay meaning of terminal branches, beyond JSON shape and reference existence. */
class DialogueBranchesTest {
    @Test void everyRefusalEndsWithoutBattlePurchaseOrCompletion() {
        var r=new NarrativeRegistry();r.load();int reviewed=0;
        for(var scene:r.scenes.values()) {
            if(scene.id().startsWith("outcome."))continue;
            var start=r.node(scene,scene.start());
            var refusal=Arrays.stream(start.choices()).filter(c->c.id().equals("decline")).findFirst().orElseThrow();
            assertEquals("",refusal.action(),scene.id());
            var reply=r.node(scene,refusal.next());assertEquals("declined",reply.id(),scene.id());
            for(var exit:reply.choices()) {assertEquals("close",exit.action(),scene.id());assertTrue(exit.next().isBlank(),scene.id());}
            var acceptance=Arrays.stream(start.choices()).filter(c->c.id().equals("accept")).findFirst().orElseThrow();
            assertNotEquals(acceptance.next(),refusal.next(),scene.id());reviewed++;
        }
        assertEquals(311,reviewed);
    }
    @Test void onlyAnExplicitBattleConfirmationStartsABattle() {
        var r=new NarrativeRegistry();r.load();
        for(var stage:r.stages.values()) {
            var scene=r.scenes.get(stage.scene());
            long battleActions=Arrays.stream(scene.nodes()).flatMap(n->Arrays.stream(n.choices())).filter(c->c.action().equals("battle")).count();
            assertEquals(stage.type().equals("battle")?1:0,battleActions,stage.id());
            if(stage.type().equals("battle")) {
                var accepted=r.node(scene,"accepted");assertEquals("battle",accepted.choices()[0].action());
            }
        }
    }
    @Test void shoppingIsOnlyAnOptionalZeroRewardActivity() {
        var r=new NarrativeRegistry();r.load();
        assertFalse(Arrays.stream(r.data.campaign()).anyMatch(s->s.type().equals("buy")));
        var ren=r.chains.get(NarrativeMigration.OPTIONAL_SUPPLIES);
        assertNotNull(ren);assertEquals(0,ren.reward());assertEquals("ren_supplies",ren.stages()[0].id());
        assertTrue(r.scenes.containsKey("ren_supplies"));
    }
}
