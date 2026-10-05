package vn.worldcomesalive.generation.v2;
import java.io.*;
import java.util.*;
import com.google.gson.Gson;
/** Versioned high-level content: economic programs, district graphs, lots, massing and room compositions. */
public final class GenerationCatalog {
    public record DistrictDef(String id,String kind,int x,int z,int radius,double density){}
    public record LinkDef(String from,String to,String road){}
    public record Archetype(String id,int minPopulation,int maxPopulation,int foodCellsPerPerson,int minFields,List<String> economies,List<DistrictDef> districts,List<LinkDef> links,Map<String,Integer> services){}
    public record RoadDef(int width,String material,double slopeCost,boolean lighting){}
    public record RoomDef(String type,int x,int z,int width,int depth,int floor,String composition,int beds){}
    public record BuildingDef(String id,String type,String profession,int width,int depth,int floors,int storey,String roof,int beds,int lotMargin,List<RoomDef> rooms,List<String> exterior){}
    public record Atom(String kind,int x,int y,int z,String facing,String layer,List<String> markers){}
    public record ExteriorDef(String anchor,List<Atom> atoms){}
    public Map<String,ExteriorDef> exteriors;
    public record Composition(String id,String roomType,List<Atom> atoms){}
    public Map<String,Archetype> archetypes;
    public Map<String,RoadDef> roads;
    public Map<String,BuildingDef> buildings;
    public Map<String,Composition> compositions;
    public Map<String,List<String>> residential;
    public static volatile GenerationCatalog active=defaults();
    public static GenerationCatalog defaults(){try(var in=GenerationCatalog.class.getResourceAsStream("/data/worldcomesalive/generation/v2.json")){return read(new InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8));}catch(IOException e){throw new UncheckedIOException(e);}}
    public static GenerationCatalog read(Reader r){var c=new Gson().fromJson(r,GenerationCatalog.class);c.validate();return c;}
    public void validate(){if(archetypes==null||archetypes.size()<2||roads==null||buildings==null||compositions==null||residential==null)throw new IllegalArgumentException("Incomplete V2 generation catalog");for(String id:List.of("REGIONAL_ROUTE","MAIN_STREET","SECONDARY_STREET","FARM_TRACK","FOOTPATH"))if(!roads.containsKey(id)||roads.get(id).width<1||roads.get(id).width>5)throw new IllegalArgumentException("Missing/invalid road "+id);
        for(var a:archetypes.values()){if(a.minPopulation<12||a.maxPopulation<a.minPopulation||a.maxPopulation>120||a.foodCellsPerPerson<8||a.districts==null||a.links==null||a.services==null)throw new IllegalArgumentException("Invalid settlement program "+a.id);Set<String> nodes=new HashSet<>();a.districts.forEach(d->{if(!nodes.add(d.id))throw new IllegalArgumentException("Duplicate district");});for(var l:a.links)if(!nodes.contains(l.from)||!nodes.contains(l.to)||!roads.containsKey(l.road))throw new IllegalArgumentException("Invalid macro edge");a.services.forEach((id,n)->{if(!buildings.containsKey(id)||n<1)throw new IllegalArgumentException("Unknown building program "+id);});}
        for(var b:buildings.values()){if(b.width<7||b.depth<7||b.floors<1||b.floors>3||b.storey<4||b.rooms==null||b.rooms.isEmpty())throw new IllegalArgumentException("Invalid massing "+b.id);int beds=0;for(var room:b.rooms){if(room.x<1||room.z<1||room.x+room.width>=b.width||room.z+room.depth>=b.depth||room.floor>=b.floors||room.width<3||room.depth<3||!compositions.containsKey(room.composition))throw new IllegalArgumentException("Invalid room program "+b.id+":"+room.type);beds+=room.beds;}if(beds!=b.beds)throw new IllegalArgumentException("Residential beds must be programmed before population "+b.id);}
        if(exteriors==null)throw new IllegalArgumentException("Missing exterior modules");for(var b:buildings.values())for(String id:b.exterior)if(!exteriors.containsKey(id))throw new IllegalArgumentException("Unknown exterior composition "+id);
        for(var ids:residential.values())for(String id:ids)if(!buildings.containsKey(id))throw new IllegalArgumentException("Unknown residential program");for(var c:compositions.values())for(var atom:c.atoms)if(atom.markers==null||!Set.of("primary","secondary","detail").contains(atom.layer))throw new IllegalArgumentException("Invalid composition atom");
    }
}
