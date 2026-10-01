package vn.svarcade.tcg;

import org.junit.jupiter.api.Test;
import vn.svarcade.tcg.data.Catalog;
import vn.svarcade.tcg.duel.Duel;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class DuelPileVisibilityTest {
    private Duel duel() throws Exception {
        Catalog base = Catalog.load(Path.of("nonexistent-test-catalog.json"));
        var r = base.rules();
        Catalog catalog = new Catalog(new Catalog.Rules(5,60,15,3,5,8000,5,5,1,300,r.rankedLimits(),r.effective()),
            base.cards(),base.banners(),base.rewards(),base.dealers(),base.starters());
        return new Duel(catalog,List.of("flamethrower","feint","charmander","protect","exit"),List.of("mega_charizard"),
            List.of("protect","feint","squirtle","potion","research"),List.of(),new Random(17));
    }
    @Test void extraCountsArePublicWithoutRevealingHiddenCards() throws Exception {
        Duel duel = duel();
        assertEquals(List.of(1,0),duel.view(1).extraCounts());
        assertEquals(List.of(1,0),duel.spectatorView().extraCounts());
        assertTrue(duel.view(1).cards().stream().noneMatch(c -> c.zone() == Duel.Zone.EXTRA));
        assertTrue(duel.spectatorView().cards().stream().noneMatch(c -> c.zone() == Duel.Zone.EXTRA || c.zone() == Duel.Zone.DECK));
        duel.qaPrepareCreationScenario(0);
        assertEquals(List.of(3,0),duel.spectatorView().extraCounts());
        duel.qaSummonCreation(0); // Normal authoritative summon consumes an Extra Deck card.
        assertEquals(List.of(2,0),duel.view(1).extraCounts());
        assertEquals(3,duel.view(1).cards().stream().filter(c -> c.zone() == Duel.Zone.DISCARD).count());
        duel.qaTickCreation(0);duel.qaTickCreation(0);
        assertEquals(List.of(1,0),duel.spectatorView().extraCounts());
        assertEquals(1,duel.spectatorView().cards().stream().filter(c -> c.zone() == Duel.Zone.BANISHED).count());
    }
    @Test void graveyardTopFollowsResolutionOrderRatherThanOriginalDeckOrder() throws Exception {
        Duel duel = duel();
        for (int i = 0; i < 2; i++) {
            duel.act(0,new Duel.Action("next","",""),duel.revision());
            duel.act(1,new Duel.Action("pass","",""),duel.revision());
        }
        String flame = token(duel,0,"Flamethrower"), protect = token(duel,1,"Protect"), feint = token(duel,0,"Feint");
        duel.act(0,new Duel.Action("activate",flame,""),duel.revision());
        duel.act(1,new Duel.Action("activate",protect,"1"),duel.revision());
        duel.act(0,new Duel.Action("activate",feint,"2"),duel.revision());
        duel.act(1,new Duel.Action("pass","",""),duel.revision());
        duel.act(0,new Duel.Action("pass","",""),duel.revision());
        var graveyard = duel.spectatorView().cards().stream()
            .filter(c -> c.controller() == 0 && c.zone() == Duel.Zone.DISCARD).toList();
        assertEquals(2,graveyard.size());
        assertEquals(flame,graveyard.getLast().token());
    }
    private String token(Duel duel,int seat,String name) {
        return duel.view(seat).cards().stream().filter(c -> c.controller() == seat && c.name().equals(name))
            .findFirst().orElseThrow().token();
    }
}
