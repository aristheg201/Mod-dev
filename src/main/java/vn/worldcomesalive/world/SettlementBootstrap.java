package vn.worldcomesalive.world;
import vn.worldcomesalive.model.LivingWorld;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.data.WorldContent;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Terrain-independent deterministic topology -> semantics -> capacity -> households -> professions. */
public final class SettlementBootstrap {
    public static Settlement create(long seed,Pos center,String biome,WorldContent data,String archetype){
        Random rng=new Random(seed);Settlement s=new Settlement();s.seed=seed;s.id="settlement_"+Long.toUnsignedString(seed,36);s.center=center;
        var region=data.region(biome);s.region=region.id();s.faction=region.faction();s.archetype=archetype;
        s.name=data.lastNames.get(rng.nextInt(data.lastNames.size()))+List.of("ford","haven","ridge","mere").get(rng.nextInt(4));
        var shape=data.archetypes.stream().filter(a->a.id().equals(archetype)).findFirst().orElse(data.archetypes.getFirst());
        int index=0;
        for(var p:shape.plots()){
            Building b=new Building();b.id=s.id+"_"+p.type()+"_"+index++;b.type=p.type();b.profession=p.profession();b.width=p.width();b.depth=p.depth();b.height=4;b.beds=p.beds();b.origin=new Pos(center.x()+p.x(),center.y(),center.z()+p.z());
            Pos entrance=new Pos(b.origin.x()+b.width/2.0,center.y()+1,b.origin.z()+b.depth-1.5);
            b.mark(Marker.ENTRANCE,entrance);b.mark(Marker.DOOR,entrance);b.mark(Marker.ROAD_CONNECTION,new Pos(entrance.x(),center.y()+1,0+center.z()));
            for(String m:p.markers())if(!m.equals("BED"))b.mark(Marker.valueOf(m),new Pos(b.origin.x()+b.width/2.0,center.y()+1,b.origin.z()+b.depth/2.0));
            for(int bed=0;bed<b.beds;bed++)b.mark(Marker.BED,new Pos(b.origin.x()+2+(bed%3)*2,center.y()+1,b.origin.z()+2+(bed/3)*3));
            b.stock.put("minecraft:bread",b.type.equals("bakery")?24:2);b.stock.put("minecraft:wheat",b.type.equals("farm")?36:0);if(b.type.equals("forge"))b.stock.put("minecraft:iron_ingot",24);
            s.buildings.put(b.id,b);s.roads.add(b.point(Marker.ROAD_CONNECTION));
        }
        s.roads.sort(Comparator.comparingDouble(Pos::x));return s;
    }
    public static void populate(LivingWorld world,Settlement s,WorldContent data,List<String> deck){
        if(!s.residents.isEmpty())return;Random rng=new Random(s.seed^0x5D3319L);List<Npc> adults=new ArrayList<>();
        for(Building b:s.buildings.values())if(b.beds>0){
            Household h=new Household();h.id=uuid(b.id+"household");h.home=b.id;h.name=data.lastNames.get(rng.nextInt(data.lastNames.size()));s.households.put(h.id,h);b.owner=h.id.toString();
            for(int i=0;i<b.beds;i++){
                Npc n=new Npc();n.id=uuid(b.id+"resident_"+i);n.gender=rng.nextBoolean()?"male":"female";List<String> names=data.genderNames.getOrDefault(n.gender,data.firstNames);n.name=names.get(rng.nextInt(names.size()))+" "+h.name;n.household=h.id;n.home=b.id;n.settlement=s.id;n.faction=s.faction;n.age=i<2?22+rng.nextInt(30):8+rng.nextInt(9);n.lifeStage=n.age<18?"child":"adult";n.birthday=rng.nextInt(96);n.location=b.point(Marker.ENTRANCE);n.nextCognition=world.clock+rng.nextInt(100);n.lastCognition=world.clock;n.appearance=s.region+":"+rng.nextInt(8);n.schedule.put("work_start",data.professions.get("resident").start());n.schedule.put("work_end",data.professions.get("resident").end());n.schedule.put("sleep",13000);n.interests.add(n.trait("curiosity")>.5?"pokemon":"food");n.knownLocations.addAll(s.buildings.keySet());n.ownership.add(b.id);n.inventory.put("minecraft:bread",2);
                for(String trait:data.traits)n.personality.put(trait,0.1+rng.nextDouble()*0.8);n.needs.put("hunger",rng.nextDouble()*.4);n.needs.put("fatigue",rng.nextDouble()*.2);n.needs.put("loneliness",rng.nextDouble()*.2);
                if(n.age>=18){adults.add(n);if(rng.nextDouble()<.45){n.cardArchetype=List.of("casual","collector","competitive","scholar").get(rng.nextInt(4));setDeck(n,deck);}}
                h.members.add(n.id);s.residents.add(n.id);world.npcs.put(n.id,n);
            }
            for(UUID a:h.members)for(UUID c:h.members)if(!a.equals(c)){Relationship r=world.npcs.get(a).relationship(c);r.family=world.npcs.get(c).age<18?"child":"family";r.friendship=65;r.trust=75;r.loyalty=85;r.familiarity=100;}
        }
        Set<UUID> employed=new HashSet<>();for(Building b:s.buildings.values())if(!b.profession.equals("resident")){
            Npc n=adults.stream().filter(a->a.home.equals(b.id)&&!employed.contains(a.id)).findFirst().orElseGet(()->adults.stream().filter(a->!employed.contains(a.id)).findFirst().orElse(null));if(n==null)continue;
            employed.add(n.id);n.profession=b.profession;n.workplace=b.id;n.skills.put(data.professions.get(n.profession).skill(),.15);var prof=data.professions.get(n.profession);n.schedule.put("work_start",prof.start());n.schedule.put("work_end",prof.end());
            if(!prof.pokemon().isBlank()){Partner partner=new Partner();partner.id=uuid(n.id+"partner");partner.species=prof.pokemon();partner.role=prof.pokemonRole();n.pokemon.add(partner);}
            if(n.profession.equals("innkeeper")){n.cardArchetype="gambler";setDeck(n,deck);}
        }
        s.ready=true;world.revision++;
    }
    private static void setDeck(Npc n,List<String> deck){if(deck.isEmpty())return;n.decks.put("casual",new ArrayList<>(deck));n.decks.put("serious",new ArrayList<>(deck));n.decks.put("tournament",new ArrayList<>(deck));for(String card:deck)n.collection.merge(card,1,Integer::sum);n.skills.put("cards",.1+n.trait("curiosity")*.4);}
    public static UUID uuid(String key){return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));}
    private SettlementBootstrap(){}
}
