package vn.worldcomesalive.generation.v2;
import java.util.*;
import static vn.worldcomesalive.generation.v2.Spatial.*;
/** Persistable spatial world program, including authored composition choices and validation evidence. */
public final class SettlementPlan {
    public int generationVersion=2,orientation;
    public SettlementProgram program;
    public Point center;public DungeonPlan dungeon;
    public record PublicSpace(String id,String kind,Rect boundary){}
    public List<PublicSpace> publicSpaces=new ArrayList<>();
    public List<District> districts=new ArrayList<>();
    public List<Road> roads=new ArrayList<>();
    public List<Lot> lots=new ArrayList<>();
    public List<Field> fields=new ArrayList<>();
    public List<Building> buildings=new ArrayList<>();
    public Map<String,Double> quality=new LinkedHashMap<>();
    public List<String> failures=new ArrayList<>();
    public record District(String id,String kind,Point center,int radius,double density){}
    public record Road(String id,String from,String to,String kind,List<Point> points){}
    public record Lot(String id,String district,String kind,Rect boundary,String building,Point frontage,Point gate,double wealth){}
    public record Field(String id,String kind,Rect boundary,String crop,int capacity,String ownerLot,String storageBuilding){}
    public record Room(String id,String type,Rect bounds,int floor,String composition,int beds){}
    public record Building(String id,String program,Rect bounds,int foundation,int rotation,int floors,String roof,String lot,List<Room> rooms){
        public Point local(double x,double z){int w=rotation%2==0?bounds.width():bounds.depth(),d=rotation%2==0?bounds.depth():bounds.width();return switch(rotation){case 1->new Point(bounds.x()+d-1-z,bounds.z()+x);case 2->new Point(bounds.x()+w-1-x,bounds.z()+d-1-z);case 3->new Point(bounds.x()+z,bounds.z()+w-1-x);default->new Point(bounds.x()+x,bounds.z()+z);};}
    }
    public District district(String id){return districts.stream().filter(d->d.id.equals(id)).findFirst().orElseThrow();}
}
