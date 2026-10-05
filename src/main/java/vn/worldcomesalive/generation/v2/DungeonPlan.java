package vn.worldcomesalive.generation.v2;
import java.util.*;
import static vn.worldcomesalive.generation.v2.Spatial.*;
/** Semantic location graph precedes room geometry. Persistent occupants and clearance are world facts. */
public final class DungeonPlan {
    public String id,archetype,history,faction,state="UNEXPLORED";public Point anchor;public int surface;public long discoveredAt,clearedAt;
    public List<Chamber> chambers=new ArrayList<>();public List<Link> links=new ArrayList<>();public Map<UUID,Occupant> occupants=new LinkedHashMap<>();
    public static final class Loot {public int x,y,z;public String room;public boolean created;public Map<String,Integer> items=new LinkedHashMap<>();}
    public List<Loot> loot=new ArrayList<>();
    public record Chamber(String id,String role,String zone,Rect bounds,int level,String composition,int lootBudget){}
    public record Link(String from,String to,boolean mandatory){}
    public static final class Occupant {public UUID id;public String room,species;public boolean alive=true,spawned;public int danger;}
    public boolean connected(){Set<String> reached=new HashSet<>(Set.of("entrance"));boolean changed;do{changed=false;for(var edge:links)if(reached.contains(edge.from)||reached.contains(edge.to)){changed|=reached.add(edge.from);changed|=reached.add(edge.to);}}while(changed);return chambers.stream().allMatch(r->reached.contains(r.id));}
    public boolean death(UUID npc,long now){var occupant=occupants.get(npc);if(occupant==null||!occupant.alive)return false;occupant.alive=false;if(occupants.values().stream().noneMatch(o->o.alive)){state="CLEARED";clearedAt=now;}else state="PARTIALLY_CLEARED";return true;}
}
