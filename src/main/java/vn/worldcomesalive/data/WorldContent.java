package vn.worldcomesalive.data;

import com.google.gson.Gson;
import vn.worldcomesalive.model.LivingWorld.Marker;
import java.io.*;
import java.util.*;

/** Validated reload snapshot. Candidate indexing happens at reload, never at interaction. */
public final class WorldContent {
    public List<String> firstNames,lastNames,traits;
    public List<Region> regions;
    public List<Archetype> archetypes;
    public Map<String,Profession> professions;
    public List<Dialogue> dialogue;
    public List<Action> actions;
    public List<Recipe> recipes;
    public List<EventDef> events;
    public List<Gift> gifts;
    public int regionChunks=24,relevance=80;
    private transient Map<String,List<Dialogue>> index;
    public record Region(String id,String biome,String wood,String stone,List<String> food,List<String> culture,String faction) {}
    public record Archetype(String id,List<Plot> plots) {}
    public record Plot(String type,String profession,int x,int z,int width,int depth,int beds,List<String> markers) {}
    public record Profession(String pokemon,String pokemonRole,int start,int end,String skill) {}
    public record Dialogue(String id,String topic,String profession,String activity,double minTrust,String memory,String season,String weather,String text) {}
    public record Action(String id,Set<String> requires,Set<String> adds,int cost,String execution) {}
    public record Recipe(String profession,String input,int consumed,String output,int produced,long wage) {}
    public record EventDef(String type,int duration,double chance) {}
    public record Gift(String tag,String profession,double value,String need) {}
    public static WorldContent defaults(){try(var in=WorldContent.class.getResourceAsStream("/data/worldcomesalive/living_world/default.json")){return read(new InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8));}catch(IOException e){throw new UncheckedIOException(e);}}
    public static WorldContent read(Reader r){WorldContent c=new Gson().fromJson(r,WorldContent.class);c.compile();return c;}
    public void compile(){
        if(firstNames==null||firstNames.isEmpty()||lastNames==null||lastNames.isEmpty()||traits==null||regions==null||regions.isEmpty()||archetypes==null||archetypes.isEmpty()||professions==null||dialogue==null||actions==null||recipes==null||events==null||gifts==null||regionChunks<8||relevance<24||relevance>128)throw new IllegalArgumentException("Incomplete living-world content");
        Set<String> ids=new HashSet<>();
        for(var a:archetypes){if(!ids.add(a.id)||a.plots==null||a.plots.isEmpty()||a.plots.size()>64)throw new IllegalArgumentException("Invalid archetype "+a.id);for(var p:a.plots){if(p.beds<0||p.beds>8||p.width<7||p.width>21||p.depth<7||p.depth>21||!professions.containsKey(p.profession))throw new IllegalArgumentException("Invalid plot "+p.type);for(String marker:p.markers)Marker.valueOf(marker);}}
        index=new HashMap<>();for(var d:dialogue)index.computeIfAbsent(d.topic+":"+d.profession,k->new ArrayList<>()).add(d);
        if(!index.containsKey("talk:*"))throw new IllegalArgumentException("Missing fallback dialogue");
    }
    public List<Dialogue> candidates(String topic,String profession){List<Dialogue> out=new ArrayList<>(index.getOrDefault(topic+":"+profession,List.of()));out.addAll(index.getOrDefault(topic+":*",List.of()));return out;}
    public Region region(String biome){return regions.stream().filter(r->!r.biome.equals("*")&&biome.contains(r.biome)).findFirst().orElse(regions.getLast());}
}
