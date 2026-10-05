package vn.worldcomesalive.furniture;
import vn.worldcomesalive.model.LivingWorld.*;
import java.util.*;

/** Authored room compositions: anchors -> zones -> three furniture layers -> circulation proof. */
public final class FurnitureLayout {
    public record Piece(String type,int x,int y,int z,String facing,String layer,String cluster){public Piece(String type,int x,int y,int z,String facing){this(type,x,y,z,facing,"secondary","structure");}}
    public record Room(String type,int x,int z,int width,int depth,String variant){}
    public record Validation(boolean valid,int walkable,int floorCells,int primary,int secondary,int details,List<String> failures){}
    public static final int VERSION=4;
    private final Building b;
    private final FurnishingContent.Style style;
    private final boolean mirror;
    private final List<Piece> pieces=new ArrayList<>();
    private final Map<Marker,List<Pos>> points=new EnumMap<>(Marker.class);
    private int details;
    private FurnitureLayout(Building b){this.b=b;style=FurnishingContent.active.style(b.region,b.wealth);mirror=Math.floorMod(b.id.hashCode(),2)==1;}
    public static List<Piece> plan(Building b){var g=new FurnitureLayout(b);g.compose();return List.copyOf(g.pieces);}
    private void room(String type,int x,int z,int width,int depth){b.rooms.add(new Room(type,mirror?b.width-x-width:x,z,width,depth,mirror?"reflected":"hearthside"));}
    private void compose(){
        b.rooms.clear();b.furnishingStyle=style.id();
        if(b.type.equals("barn")){barn();}else if(b.type.equals("tavern")&&b.width>=17&&b.depth>=17)tavern();else house();
        // Upper-volume beams and lighting connect zones without occupying circulation at head height.
        for(int x=1;x<b.width-1;x++)piece("timber_beam",x,3,b.type.equals("tavern")?6:5,"north","secondary","ceiling");
        light(b.width/2,3,b.depth-3);light(2,3,3);if(b.width>=17)light(b.width-4,3,4);
        points.forEach((marker,list)->b.markers.put(marker,List.copyOf(list)));
        if(!points.containsKey(Marker.SOCIAL_POINT))b.markers.put(Marker.SOCIAL_POINT,b.markers.getOrDefault(Marker.DINING_POINT,List.of(b.origin)));
        if(b.type.equals("tavern")){List<Pos> publicSeats=new ArrayList<>(points.getOrDefault(Marker.TAVERN_SEAT,List.of()));b.markers.put(Marker.TAVERN_SEAT,List.copyOf(publicSeats));b.seatCapacity=publicSeats.size();b.standingCapacity=2;b.serviceCapacity=2;}
        var report=validate(b,pieces);
        // A smaller storage composition is preferred to sealing a circulation pocket.
        if(!report.valid()){List<Pos> storage=new ArrayList<>(b.markers.getOrDefault(Marker.FOOD_STORAGE,List.of()));for(Pos pos:new ArrayList<>(storage)){int x=(int)Math.floor(pos.x()-b.origin.x()),z=(int)Math.floor(pos.z()-b.origin.z());if(report.failures().contains("inaccessible FOOD_STORAGE at "+x+":"+z)){storage.remove(pos);pieces.removeIf(p->p.x==x&&p.z==z&&p.y==1&&Set.of("food_pantry","medieval_cabinet","wardrobe","crate","storage_chest").contains(p.type));}}b.markers.put(Marker.FOOD_STORAGE,List.copyOf(storage));report=validate(b,pieces);}b.furnishingValid=report.valid();b.furnishingFailures=new ArrayList<>(report.failures());
        if(!report.valid())throw new IllegalArgumentException("Furnishing circulation failed for "+b.id+": "+report.failures()+" floor="+pieces.stream().filter(p->p.y==1).toList());
    }
    private void barn(){room("AGRICULTURAL_STORAGE",1,1,b.width-2,b.depth-2);for(int x=1;x<=3;x++)for(int z=1;z<=3;z++)piece("hay_storage",x,1,z,"north","primary","hayloft");for(int x=8;x<b.width-1;x++){piece("crate",x,1,2,"south","secondary","granary");piece("flour_sack",x,2,2,"north","detail","granary");}piece("tool_rack",b.width-2,2,4,"west","detail","equipment");piece("work_table",2,1,b.depth-3,"south","primary","carpenter");piece("tool_samples",2,2,b.depth-3,"north","detail","carpenter");mark(Marker.WORKSTATION,3,1,b.depth-3);mark(Marker.FOOD_STORAGE,8,1,2);mark(Marker.STORAGE,8,1,2);}
    private void house(){
        room(b.type.equals("guardhouse")?"GUARD_BARRACKS":"MASTER_BEDROOM",1,1,4,4);
        cluster("master_bedroom",1,1,1);
        if(b.beds>=3){room("CHILD_BEDROOM",6,1,4,4);cluster("child_bedroom",6,1,1);}
        else{room(b.type.equals("guild_hall")?"STUDY":"PANTRY",6,1,4,4);cluster("reading_corner",7,1,1);piece("food_pantry",9,1,4,"west","secondary","pantry");mark(Marker.FOOD_STORAGE,9,1,4);}
        // Partition private sleeping spaces; the central two-block entry remains open.
        for(int z=1;z<=3;z++)for(int y=1;y<=3;y++)piece("partition",5,y,z,"north","secondary","privacy");
        if(b.beds>=4){piece("bed_foot",7,1,1,"east","primary","child_bedroom");piece("bed_head",8,1,1,"east","primary","child_bedroom");mark(Marker.BED,7,1,1);}
        room("DINING_ROOM",1,6,4,4);cluster("dining_family",2,1,b.depth-5);
        if(b.beds>=4){piece("wooden_stool",3,1,b.depth-6,"south","primary","dining_family");mark(Marker.DINING_POINT,3,1,b.depth-6);}
        room("KITCHEN",7,4,3,3);cluster("kitchen_corner",7,1,4);
        room("LIVING_ROOM",1,4,4,2);cluster("hearth_corner",1,1,5);
        room("STUDY",7,7,3,3);cluster("reading_corner",7,1,b.depth-3);
        if(b.type.equals("forge")){room("BLACKSMITH_WORKSPACE",6,4,4,3);cluster("smith_workspace",6,1,5);}
        else if(Set.of("farm","bakery","trading_post").contains(b.type)){room("SHOP_FLOOR",6,6,4,2);cluster("merchant_display",6,1,6);}
        if(b.profession.equals("farmer"))cluster("personal_farmer",1,1,3);else if(b.profession.equals("healer"))cluster("personal_scholar",1,1,8);
        if(b.visualPersonality.getOrDefault("curiosity",.5)>.6)piece("paper_quill",8,2,b.depth-2,"north","detail","personal_scholar");
        if(b.cardInterest)piece("card_set",3,2,b.depth-4,"north","detail","card_collection");
        if(b.type.equals("tavern")){points.put(Marker.TAVERN_SEAT,new ArrayList<>(points.getOrDefault(Marker.DINING_POINT,List.of())));mark(Marker.CARD_DUEL_TABLE,3,1,b.depth-4);mark(Marker.DINING_TABLE,3,1,b.depth-4);}
        // Interaction markers refer to reachable positions adjacent to their physical workstation.
        if(!b.profession.equals("resident"))mark(Marker.WORKSTATION,6,1,4);
        mark(Marker.CUSTOMER_POINT,6,1,7);mark(Marker.STORAGE,9,1,4);
    }
    private void tavern(){
        room("TAVERN_GUEST_ROOM",1,1,7,5);cluster("tavern_guest_beds",2,1,1);
        for(int z=1;z<=4;z++)for(int y=1;y<=3;y++)piece("partition",8,y,z,"north","secondary","guest_privacy");
        if(b.beds>=3){piece("bed_foot",6,1,4,"north","primary","guest_beds");piece("bed_head",6,1,3,"north","primary","guest_beds");mark(Marker.BED,6,1,4);}
        if(b.beds>=4){piece("bed_foot",2,1,4,"north","primary","guest_beds");piece("bed_head",2,1,3,"north","primary","guest_beds");mark(Marker.BED,2,1,4);}
        room("TAVERN_KITCHEN",10,1,6,5);cluster("tavern_kitchen",12,1,1);
        room("TAVERN_BAR",11,5,5,6);cluster("bar_service",11,1,6);
        room("TAVERN_COMMON_ROOM",1,8,7,7);cluster("long_dining_group",2,1,9);cluster("small_tavern_group",5,1,13);
        room("FIREPLACE_SOCIAL_CORNER",1,5,5,3);cluster("hearth_corner",1,1,6);
        room("CARD_WORLDS_AREA",10,11,6,5);cluster("card_corner",11,1,12);
        piece("armchair",4,1,6,"west","primary","hearth_corner");mark(Marker.SOCIAL_POINT,4,1,6);
        for(int z=8;z<=14;z++)piece("rug",8,1,z,"north","detail","entrance_runner");
        mark(Marker.SERVING_POINT,10,1,8);mark(Marker.CUSTOMER_POINT,10,1,9);mark(Marker.WORKSTATION,12,1,6);mark(Marker.WORKSTATION,14,1,6);mark(Marker.STORAGE,15,1,4);
        mark(Marker.SOCIAL_POINT,8,1,12);mark(Marker.SOCIAL_POINT,9,1,10);
    }
    private void cluster(String name,int x,int y,int z){var atoms=FurnishingContent.active.clusters.get(name);if(atoms==null)throw new IllegalArgumentException("Missing cluster "+name);for(var a:atoms){String type=a.type().equals("@seat")?style.seat():a.type().equals("@storage")?style.storage():a.type();int px=x+a.x(),py=y+a.y(),pz=z+a.z();if(px<1||px>=b.width-1||pz<1||pz>=b.depth-1)continue;if(a.layer().equals("detail")&&details>=style.decorationBudget())continue;
        piece(type,px,py,pz,a.facing(),a.layer(),name);for(String marker:a.markers())mark(Marker.valueOf(marker),px,py,pz);}}
    private void piece(String type,int x,int y,int z,String facing,String layer,String cluster){int xx=mirror?b.width-1-x:x;String direction=mirror?facing.equals("east")?"west":facing.equals("west")?"east":facing:facing;Piece p=new Piece(type,xx,y,z,direction,layer,cluster);
        // Replacement within a deliberate cluster is allowed; incidental intersections are not retained.
        pieces.removeIf(existing->existing.x==p.x&&existing.y==p.y&&existing.z==p.z);pieces.add(p);if(layer.equals("detail"))details++;}
    private void mark(Marker marker,int x,int y,int z){int xx=mirror?b.width-1-x:x;points.computeIfAbsent(marker,k->new ArrayList<>()).add(new Pos(b.origin.x()+xx+.5,b.origin.y()+y,b.origin.z()+z+.5));}
    private void light(int x,int y,int z){piece("hanging_lantern",x,y,z,"north","detail","lighting");}
    public static Validation validate(Building b,List<Piece> pieces){
        Set<String> blocked=new HashSet<>(),walked=new HashSet<>();List<String> failures=new ArrayList<>();int primary=0,secondary=0,details=0;
        for(Piece p:pieces){if(p.layer.equals("primary"))primary++;else if(p.layer.equals("secondary"))secondary++;else details++;if(p.x<1||p.x>=b.width-1||p.z<1||p.z>=b.depth-1)failures.add("outside room "+p.type);if(p.y==1&&!Set.of("rug","firewood","flour_sack","produce_basket").contains(p.type))blocked.add(p.x+":"+p.z);}
        ArrayDeque<int[]> queue=new ArrayDeque<>();queue.add(new int[]{b.width/2,b.depth-2});while(!queue.isEmpty()){int[] p=queue.remove();String key=p[0]+":"+p[1];if(p[0]<1||p[0]>=b.width-1||p[1]<1||p[1]>=b.depth-1||blocked.contains(key)||!walked.add(key))continue;for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})queue.add(new int[]{p[0]+d[0],p[1]+d[1]});}
        if(walked.isEmpty())failures.add("blocked entrance");for(Marker marker:List.of(Marker.BED,Marker.DINING_POINT,Marker.WORKSTATION,Marker.FOOD_STORAGE))for(Pos pos:b.markers.getOrDefault(marker,List.of())){int x=(int)Math.floor(pos.x()-b.origin.x()),z=(int)Math.floor(pos.z()-b.origin.z());boolean reachable=walked.contains(x+":"+z);for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})reachable|=walked.contains((x+d[0])+":"+(z+d[1]));if(!reachable)failures.add("inaccessible "+marker+" at "+x+":"+z);}
        return new Validation(failures.isEmpty(),walked.size(),(b.width-2)*(b.depth-2),primary,secondary,details,List.copyOf(failures));
    }
    private FurnitureLayout() {throw new AssertionError();}
}
