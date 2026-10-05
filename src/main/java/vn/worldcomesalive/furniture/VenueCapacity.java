package vn.worldcomesalive.furniture;
import java.util.*;
import vn.worldcomesalive.model.LivingWorld.*;
/** Persistent occupancy is shared by visible and abstract NPCs. A reservation has one unique location. */
public final class VenueCapacity {
    public static Pos reserve(Building venue,Npc visitor){if(venue.visitors.containsKey(visitor.id))return venue.visitors.get(visitor.id);List<Pos> seats=venue.markers.getOrDefault(Marker.TAVERN_SEAT,List.of());for(int offset=0;offset<seats.size();offset++){Pos p=seats.get(Math.floorMod(visitor.id.hashCode()+offset,seats.size()));if(!venue.visitors.containsValue(p)){venue.visitors.put(visitor.id,p);return p;}}return null;}
    public static void release(Settlement s,UUID npc){for(Building b:s.buildings.values())b.visitors.remove(npc);}
    private VenueCapacity(){}
}
