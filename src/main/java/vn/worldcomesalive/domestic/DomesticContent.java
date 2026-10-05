package vn.worldcomesalive.domestic;
import java.util.*;
import java.io.*;
import com.google.gson.Gson;

/** Reloadable public food/production metadata. Registry IDs remain stable after Minecraft initialization. */
public final class DomesticContent {
    public record Good(String name,String category,int nutrition,float saturation,long value,String container,double alcohol,List<String> tags,List<String> regions,String method,boolean quality,long freshnessTicks){}
    public record Recipe(String id,String output,Map<String,Integer> inputs,String station,int ticks,int count,List<String> roles,int quality){}
    public record Alcohol(boolean enabled,double decayPerTick,double relaxed,double tipsy,double drunk,double veryDrunk,double maxCardPenalty){}
    public Map<String,Good> goods;
    public List<Recipe> recipes;
    public Alcohol alcohol;
    public boolean spoilage=false,publicTableware=true;
    public long npcDrinkInterval=2400;
    public Map<String,Integer> initialFarmResources;
    public static volatile DomesticContent active=defaults();
    public static DomesticContent defaults(){try(var stream=DomesticContent.class.getResourceAsStream("/data/worldcomesalive/living_world/domestic.json")){return read(new InputStreamReader(Objects.requireNonNull(stream),java.nio.charset.StandardCharsets.UTF_8));}catch(IOException e){throw new UncheckedIOException(e);}}
    public static DomesticContent read(Reader reader){DomesticContent c=new Gson().fromJson(reader,DomesticContent.class);c.validate();return c;}
    public void validate(){if(goods==null||goods.isEmpty()||recipes==null||alcohol==null||initialFarmResources==null||npcDrinkInterval<200||alcohol.decayPerTick<0)throw new IllegalArgumentException("Incomplete domestic definitions");for(var e:goods.entrySet()){var g=e.getValue();if(!e.getKey().matches("[a-z0-9_]+:[a-z0-9_]+")||g.name.isBlank()||g.nutrition<0||g.value<0||g.alcohol<0||g.tags==null||g.regions==null)throw new IllegalArgumentException("Invalid domestic good "+e.getKey());}Set<String> ids=new HashSet<>();for(var r:recipes)if(!ids.add(r.id)||!goods.containsKey(r.output)||r.inputs==null||r.inputs.isEmpty()||r.inputs.values().stream().anyMatch(n->n<=0)||r.ticks<1||r.count<1||r.roles==null)throw new IllegalArgumentException("Invalid recipe "+r.id);}
    public Good good(String id){return goods.get(id);}
    public String stage(double units){return units>=alcohol.veryDrunk?"Very Drunk":units>=alcohol.drunk?"Drunk":units>=alcohol.tipsy?"Tipsy":units>=alcohol.relaxed?"Relaxed":"Sober";}
    public double sober(double value,long last,long now){return Math.max(0,value-Math.max(0,now-last)*alcohol.decayPerTick);}
    public long price(String id,String region,double trust){Good g=goods.get(id);if(g==null)return 0;double regional=g.regions.isEmpty()?1:g.regions.contains(region)?.9:1.2;return Math.max(1,Math.round(g.value*regional*(1-Math.max(0,Math.min(50,trust))/500)));}
}
