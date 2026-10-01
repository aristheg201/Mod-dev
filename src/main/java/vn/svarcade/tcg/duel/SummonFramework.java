package vn.svarcade.tcg.duel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Data-driven multi-material, multi-stage summon profiles. */
public final class SummonFramework {
    public record Material(String card, String zone, int count) {}
    public record Stage(String card, int lockTurns, String label) {}
    public record Profile(String id, List<Material> materials, List<Stage> stages) {
        public int materialCount(){ return materials.stream().mapToInt(Material::count).sum(); }
    }
    public static final class Progress {
        private final String profileId; private int stageIndex, turnsRemaining; private String token;
        public Progress(String profileId,int stageIndex,int turnsRemaining,String token){this.profileId=profileId;this.stageIndex=stageIndex;this.turnsRemaining=turnsRemaining;this.token=token;}
        public String profileId(){return profileId;} public int stageIndex(){return stageIndex;} public void stageIndex(int v){stageIndex=v;}
        public int turnsRemaining(){return turnsRemaining;} public void turnsRemaining(int v){turnsRemaining=v;}
        public String token(){return token;} public void token(String v){token=v;}
    }
    private static final Map<String,Profile> PROFILES = load();
    private SummonFramework(){}
    public static Profile byId(String id){return PROFILES.get(id);}
    public static Collection<Profile> profiles(){return PROFILES.values();}
    public static Profile forFirstStage(String cardId){return PROFILES.values().stream().filter(p->!p.stages().isEmpty()&&p.stages().getFirst().card().equals(cardId)).findFirst().orElse(null);}
    public static boolean accepts(Profile p,String cardId){return p!=null&&p.materials().stream().anyMatch(m->m.card().equals(cardId));}
    private static Map<String,Profile> load(){
        try(var in=SummonFramework.class.getResourceAsStream("/data/svarcade_tcg/summon_profiles.json")){
            if(in==null)return Map.of();
            Map<String,Profile> m=new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),new TypeToken<LinkedHashMap<String,Profile>>(){}.getType());
            return m==null?Map.of():Map.copyOf(m);
        }catch(Exception ex){throw new IllegalStateException("Unable to load summon profiles",ex);}
    }
}
