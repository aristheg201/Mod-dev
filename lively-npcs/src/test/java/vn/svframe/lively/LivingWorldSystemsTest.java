package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.actor.ActorRegistry;
import vn.svframe.lively.crime.CrimeEngine;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.faction.FactionEngine;
import vn.svframe.lively.quest.QuestRuntime;
import vn.svframe.lively.social.RomanceEngine;
import vn.svframe.lively.social.SocialEngine;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class LivingWorldSystemsTest {
    private static ActorId npc(){ return new ActorId(UUID.randomUUID(), ActorId.Kind.NPC); }

    @Test void socialRumorAndRomanceAreStateful(){
        ActorRegistry actors=new ActorRegistry(); ActorId a=npc(),b=npc();
        actors.upsert(a,"A",Map.of("kindness",.8),Map.of(),Set.of()); actors.upsert(b,"B",Map.of("kindness",.7),Map.of(),Set.of());
        SocialEngine social=new SocialEngine();
        for(int i=0;i<8;i++) social.apply(a,b,new SocialEngine.SocialDelta(.08,.09,.04,0,.05,.06,.08,"shared_event",Map.of()));
        assertTrue(social.relationship(a,b).familiarity()>.25);
        assertTrue(social.createRumor("missing",a,a,"A was seen near forest",.8,.5,Duration.ofDays(2)).confidence()>.5);
        RomanceEngine romance=new RomanceEngine(social,actors); assertTrue(romance.begin(a,b).isPresent());
    }

    @Test void crimeInvestigationUsesEvidenceNotHiddenTruth(){
        CrimeEngine engine=new CrimeEngine(); ActorId victim=npc(),suspect=npc(),other=npc();
        CrimeEngine.Crime crime=engine.create(CrimeEngine.Type.MURDER,victim,null,"market","money",Set.of(),Map.of());
        engine.addEvidence(crime.id(),CrimeEngine.EvidenceType.MOTIVE,null,suspect,.9,.9,false,Map.of());
        engine.addEvidence(crime.id(),CrimeEngine.EvidenceType.PHYSICAL,null,suspect,.8,.8,false,Map.of());
        engine.addEvidence(crime.id(),CrimeEngine.EvidenceType.ALIBI,null,other,.9,.9,false,Map.of());
        assertEquals(suspect,engine.rankSuspects(crime.id(),Set.of(suspect,other)).getFirst().suspect());
    }

    @Test void economyBusinessAndQuestLifecycleWork(){
        ActorId merchant=npc(),buyer=npc(); EconomyEngine economy=new EconomyEngine(); economy.ensureWallet(merchant,0); economy.ensureWallet(buyer,10000);
        EconomyEngine.Business shop=economy.createBusiness(merchant,"Shop","market",Map.of()); economy.setStock(shop.id(),"minecraft:bread",10,10,100,.5,.5);
        assertTrue(economy.buy(shop.id(),buyer,"minecraft:bread",2).isPresent());
        QuestRuntime quests=new QuestRuntime(); QuestRuntime.Quest q=quests.create(merchant,buyer,"Bring bread",List.of(new QuestRuntime.Objective("bread",QuestRuntime.ObjectiveType.COLLECTION,"minecraft:bread",2,false,false,Map.of())),Duration.ofDays(1),Map.of());
        quests.activate(q.id()); assertEquals(QuestRuntime.Status.COMPLETED,quests.progress(q.id(),"bread",2).orElseThrow().status());
    }

    @Test void factionPlannerProducesBoundedStrategy(){
        FactionEngine f=new FactionEngine(); FactionEngine.Faction faction=f.create("Rangers",Set.of(npc()),Map.of("money",100L),Map.of("stability",.8,"expansion",.4));
        var plans=f.plan(faction.id(),Map.of("crime",.9,"threat",.8,"scarcity",.2)); assertFalse(plans.isEmpty()); assertEquals("increase_patrol",plans.getFirst().action());
    }
}
