package io.github.aristheg201.cobblemonworld.narrative;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class NarrativeDataTest {
    @Test void oldAndPartiallyWrittenFieldsNormalizeWithoutDroppingExistingProgress(){
        var state=new Gson().fromJson("{\"schema\":1,\"main\":\"archive_record2\",\"finished\":[\"wolf_vargan\"],\"chains\":null,\"transcript\":null,\"claims\":null}",NarrativeState.class);
        state.normalize();assertEquals("archive_record2",state.main);assertTrue(state.finished.contains("wolf_vargan"));
        assertTrue(state.chains.isEmpty());assertTrue(state.claims.isEmpty());assertNotNull(state.transcript);
        var roundTrip=new Gson().fromJson(new Gson().toJson(state),NarrativeState.class);roundTrip.normalize();assertEquals(state.finished,roundTrip.finished);
    }
    @Test void everyShippedSceneHasReachableChoicesAndEverySubstantialChainHasMultipleMechanics(){
        var registry=new NarrativeRegistry();assertDoesNotThrow(registry::load);
        assertEquals(70,registry.data.campaign().length);
        long substantive=Arrays.stream(registry.data.chains()).filter(c->!c.id().equals("weather_duo_01")).count();assertTrue(substantive>=35);
        for(var chain:registry.data.chains()){
            assertTrue(chain.stages().length>=3,chain.id());
            assertTrue(Arrays.stream(chain.stages()).map(NarrativeRegistry.Stage::type).distinct().count()>=2,chain.id());
            for(var stage:chain.stages())assertTrue(registry.scenes.containsKey(stage.scene()),stage.id());
        }
    }
    @Test void archiveAndTownEightCannotBeSkippedByLinearCampaignTransitions(){
        var registry=new NarrativeRegistry();registry.load();var stages=List.of(registry.data.campaign());
        Map<String,Integer> index=new HashMap<>();for(int i=0;i<stages.size();i++)index.put(stages.get(i).id(),i);
        assertTrue(index.get("dorian_qualifier")<index.get("tower_entry"));
        assertTrue(index.get("false_victory")<index.get("tower_entry"));
        assertTrue(index.get("wolf_vargan")<index.get("archive_record1"));
        assertTrue(index.get("archive_record3")<index.get("final_unknown"));
        assertTrue(index.get("final_confrontation")<index.get("final_explanation"));
        assertFalse(Arrays.stream(stages.get(index.get("wolf_vargan")).flags()).anyMatch(f->f.equals("toba_record_access")||f.equals("toba_meeting_revealed")));
    }
    @Test void legendaryEntitlementsRetainIdentityAcrossSerialization(){
        var state=new NarrativeState();String uuid=UUID.randomUUID().toString();state.claims.put("weather_duo_01:groudon",new NarrativeState.Claim(uuid,"delivered"));
        var loaded=new Gson().fromJson(new Gson().toJson(state),NarrativeState.class);loaded.normalize();
        assertEquals(uuid,loaded.claims.get("weather_duo_01:groudon").pokemonUuid());assertEquals("delivered",loaded.claims.get("weather_duo_01:groudon").status());
        assertFalse(loaded.claims.containsKey("weather_duo_01:kyogre"));
    }
}
