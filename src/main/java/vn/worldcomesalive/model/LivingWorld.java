package vn.worldcomesalive.model;

import java.util.*;

/** Authoritative identities and simulation state. No entity or client objects belong here. */
public final class LivingWorld {
    public int schema=1;
    public long clock, revision, transactions, materializations, dematerializations, decisions;
    public Map<String,Settlement> settlements=new LinkedHashMap<>();
    public Map<UUID,Npc> npcs=new LinkedHashMap<>();
    public Map<UUID,PlayerLife> players=new LinkedHashMap<>();
    public List<WorldEvent> events=new ArrayList<>();
    public List<Transaction> ledger=new ArrayList<>();
    public Set<String> surveyed=new HashSet<>();
    public record Pos(double x,double y,double z) {
        public double distance(Pos b){return Math.sqrt((x-b.x)*(x-b.x)+(y-b.y)*(y-b.y)+(z-b.z)*(z-b.z));}
        public Pos between(Pos b,double t){return new Pos(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t);}
    }
    public enum Marker { HOME,BED,WORKSTATION,SHOP_COUNTER,STORAGE,CUSTOMER_POINT,SOCIAL_POINT,DINING_POINT,ENTRANCE,DOOR,ROAD_CONNECTION,STABLE,HORSE_OR_MOUNT_POINT,FARM_FIELD,GUARD_POST,PATROL_POINT,CARD_DUEL_TABLE,TAVERN_SEAT,FESTIVAL_POINT,OWNER_SLOT }
    public static final class Building {
        public String id,type,profession,owner="";
        public Pos origin;
        public int width,depth,height,beds;
        public Map<Marker,List<Pos>> markers=new EnumMap<>(Marker.class);
        public Map<String,Integer> stock=new LinkedHashMap<>();
        public long money=100;
        public boolean built;
        public Pos point(Marker marker){return markers.getOrDefault(marker,List.of(origin)).getFirst();}
        public void mark(Marker marker,Pos pos){markers.computeIfAbsent(marker,k->new ArrayList<>()).add(pos);}
        public boolean contains(Pos p){return p.x>=origin.x&&p.x<origin.x+width&&p.z>=origin.z&&p.z<origin.z+depth&&Math.abs(p.y-origin.y)<height+3;}
    }
    public static final class Settlement {
        public String id,name,region,archetype,faction;
        public long seed,lastEconomyDay=-1,lastSocialDay=-1;
        public Pos center;
        public Map<String,Building> buildings=new LinkedHashMap<>();
        public Map<UUID,Household> households=new LinkedHashMap<>();
        public List<UUID> residents=new ArrayList<>();
        public List<Pos> roads=new ArrayList<>();
        public Set<String> routes=new LinkedHashSet<>();
        public double prosperity=0.6;
        public boolean ready;
        public Building service(String type){return buildings.values().stream().filter(b->b.type.equals(type)).findFirst().orElse(null);}
    }
    public static final class Household {
        public UUID id;
        public String name,home;
        public List<UUID> members=new ArrayList<>();
    }
    public static final class Npc {
        public UUID id,household;
        public String name,lifeStage="adult",appearance="",profession="resident",home,workplace="",settlement,faction;
        public int age,birthday;
        public long money=30,lastCognition,nextCognition,cognitionVersion,lastGiftDay=-1,version;
        public Pos location;
        public Travel travel;
        public String goal="socialize",activity="settling in",emotion="content",interrupt="",simulation="abstract",cardArchetype="none";
        public long interruptUntil,actionUntil;
        public List<String> plan=new ArrayList<>();
        public Map<String,Double> personality=new LinkedHashMap<>(),needs=new LinkedHashMap<>(),skills=new LinkedHashMap<>(),reputation=new LinkedHashMap<>();
        public Map<String,Integer> inventory=new LinkedHashMap<>(),collection=new LinkedHashMap<>();
        public Map<String,List<String>> decks=new LinkedHashMap<>();
        public List<Partner> pokemon=new ArrayList<>();
        public Map<UUID,Relationship> relationships=new LinkedHashMap<>();
        public List<Memory> memories=new ArrayList<>();
        public Map<String,Belief> knowledge=new LinkedHashMap<>();
        public Set<String> knownLocations=new LinkedHashSet<>(),ownership=new LinkedHashSet<>(),activeEvents=new LinkedHashSet<>();
        public Map<String,Integer> repeatedGifts=new LinkedHashMap<>();
        public Relationship relationship(UUID other){return relationships.computeIfAbsent(other,k->new Relationship());}
        public double trait(String name){return personality.getOrDefault(name,0.5);}
        public double need(String name){return needs.getOrDefault(name,0.0);}
    }
    public static final class Relationship {
        public double friendship,trust,respect,attraction,fear,loyalty,familiarity,rivalry,debt;
        public String family="",stage="acquaintance";
        public long lastInteraction=-24000;
        public void clamp(){friendship=bound(friendship);trust=bound(trust);respect=bound(respect);attraction=bound(attraction);fear=bound(fear);loyalty=bound(loyalty);familiarity=Math.max(0,Math.min(100,familiarity));rivalry=bound(rivalry);}
    }
    public static final class Partner {
        public UUID id;
        public String species,role,data="";
        public int level=12;
    }
    public record Memory(String type,UUID actor,String location,long time,double importance,double emotion,double confidence,String source) {}
    public record Belief(String fact,UUID actor,double confidence,String source,long time,int evidence) {}
    public static final class Travel {
        public List<Pos> route=new ArrayList<>();
        public long departure,pausedTicks;
        public double speed=0.12;
        public String destination;
        public Pos at(long time){double remaining=Math.max(0,time-departure-pausedTicks)*speed;for(int i=1;i<route.size();i++){Pos a=route.get(i-1),b=route.get(i);double length=a.distance(b);if(remaining<=length)return a.between(b,length==0?1:remaining/length);remaining-=length;}return route.getLast();}
        public boolean arrived(long time){return at(time).distance(route.getLast())<0.05;}
    }
    public static final class PlayerLife {
        public long money=60;
        public Map<String,Double> skills=new LinkedHashMap<>();
        public Map<String,Long> bounty=new LinkedHashMap<>();
        public Set<String> property=new LinkedHashSet<>();
        public UUID spouse;
    }
    public static final class WorldEvent {
        public String id,type,settlement,state="active";
        public long starts,ends;
        public Set<UUID> witnesses=new HashSet<>();
    }
    public record Transaction(long time,String from,String to,String item,int amount,long coins,String reason) {}
    public void remember(Npc n,Memory m){n.memories.add(m);n.knowledge.put(m.type+":"+m.actor,new Belief(m.type,m.actor,m.confidence,m.source,m.time,1));consolidate(n);n.version++;}
    public void consolidate(Npc n){
        Map<String,List<Memory>> repeated=new HashMap<>();
        for(Memory m:n.memories)if(m.importance<0.7)repeated.computeIfAbsent(m.type+":"+m.actor,k->new ArrayList<>()).add(m);
        for(var e:repeated.entrySet())if(e.getValue().size()>=12){var group=e.getValue();var m=group.getLast();var old=n.knowledge.get("belief:"+e.getKey());n.knowledge.put("belief:"+e.getKey(),new Belief(m.type.equals("friendly_greeting")?"PLAYER_IS_FREQUENTLY_FRIENDLY":m.type,m.actor,0.95,"consolidated",m.time,group.size()+(old==null?0:old.evidence)));n.memories.removeAll(group);}
    }
    public void transact(String from,String to,String item,int quantity,long coins,String reason){ledger.add(new Transaction(clock,from,to,item,quantity,coins,reason));transactions++;if(ledger.size()>4096)ledger.removeFirst();revision++;}
    private static double bound(double v){return Math.max(-100,Math.min(100,v));}
}
