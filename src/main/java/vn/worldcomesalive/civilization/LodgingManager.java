package vn.worldcomesalive.civilization;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.server.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import java.util.*;
public final class LodgingManager {
    private final WorldSimulation sim;
    public final PersonalStorage storage=new PersonalStorage();
    public LodgingManager(WorldSimulation sim){this.sim=sim;}
    public void initialize(Settlement s){if(s.generationVersion>=2)return;for(var r:Lodging.rooms(s))sim.state.rooms.putIfAbsent(r.id,r);}
    public Lodging.Room at(Pos pos){return sim.state.rooms.values().stream().filter(r->r.contains(pos)).findFirst().orElse(null);}
    public List<InteractionPackets.MenuRow> menu(Npc host,UUID player){return sim.state.rooms.values().stream().filter(r->r.building.equals(host.workplace)).map(r->new InteractionPackets.MenuRow(r.id.toString(),r.name+" · "+r.quality+" · "+r.beds.size()+" bed(s) · "+r.state+(r.permitted(player,sim.state.clock)?" · yours":""),"Rooms",r.price,r.state.equals("AVAILABLE")?1:0)).toList();}
    public List<String> actions(Npc host,UUID player){List<String> a=new ArrayList<>();if(!host.profession.equals("innkeeper"))return a;for(var r:sim.state.rooms.values())if(r.building.equals(host.workplace)){if(r.state.equals("AVAILABLE"))a.add("rent:"+r.id);if(r.permitted(player,sim.state.clock)){a.add("renew:"+r.id);a.add("checkout:"+r.id);}}a.add("recover");return a;}
    public String action(ServerPlayerEntity p,Npc host,String action){var life=sim.state.players.computeIfAbsent(p.getUuid(),id->new PlayerLife());if(action.equals("recover")){storage.open(p,"recovery:"+p.getUuid(),life.recovery,"Recovered belongings",()->p.getServerWorld()==sim.world);return "Your stored belongings are safe in the recovery locker.";}String[] parts=action.split(":",2);Lodging.Room room;try{room=sim.state.rooms.get(UUID.fromString(parts[1]));}catch(RuntimeException bad){return "Unknown room.";}if(room==null||!room.building.equals(host.workplace))return "This inn does not manage that room.";Building inn=sim.state.settlements.get(room.settlement).buildings.get(room.building);
        if(parts[0].equals("rent")){if(!Lodging.rent(room,p.getUuid(),life,inn,sim.state.clock))return "This room is occupied or you cannot afford the night.";sim.state.transact(p.getUuidAsString(),inn.id,room.id.toString(),1,room.price,"one-night room rental");return "You rented "+room.name+" for one night (24,000 ticks). Your private bed and storage are upstairs. Checkout returns belongings to our recovery locker.";}
        if(parts[0].equals("renew")){if(!Lodging.renew(room,p.getUuid(),life,inn,sim.state.clock))return "Your rental has expired or you cannot afford renewal.";sim.state.transact(p.getUuidAsString(),inn.id,room.id.toString(),1,room.price,"room renewal");return "Your room is renewed for another night.";}
        if(!room.permitted(p.getUuid(),sim.state.clock))return "This is not your rental.";p.closeHandledScreen();Lodging.checkout(room,life,sim.state.clock);sim.state.revision++;return "Checked out. Your belongings remain safe in the inn's recovery locker. The room is awaiting cleaning.";
    }
    public void tick(){if(sim.state.clock%20!=0)return;for(var r:sim.state.rooms.values()){if(r.guest!=null&&r.state.equals("RENTED")&&sim.state.clock>=r.until){var p=sim.server.getPlayerManager().getPlayer(r.guest);if(p!=null){p.closeHandledScreen();p.sendMessage(Text.literal("Your inn rental has expired. Any stored belongings are safe with the innkeeper."),false);}Lodging.checkout(r,sim.state.players.computeIfAbsent(r.guest,id->new PlayerLife()),sim.state.clock);}if(r.state.equals("DIRTY")&&sim.state.clock>=r.cleanAt){r.state="AVAILABLE";sim.state.revision++;}}
    }
    public boolean permitted(ServerPlayerEntity p,BlockPos pos){var r=at(new Pos(pos.getX()+.5,pos.getY(),pos.getZ()+.5));return r==null||r.permitted(p.getUuid(),sim.state.clock);}
    public boolean openRoomStorage(ServerPlayerEntity p,BlockPos pos){var r=at(new Pos(pos.getX()+.5,pos.getY(),pos.getZ()+.5));if(r==null)return false;if(!r.permitted(p.getUuid(),sim.state.clock)){p.sendMessage(Text.literal("This guest room is private. Rent it from the innkeeper first."),true);return true;}storage.open(p,"room:"+r.id,r.contents,r.name+" · private storage",()->r.permitted(p.getUuid(),sim.state.clock)&&p.getServerWorld()==sim.world&&p.squaredDistanceTo(pos.toCenterPos())<64);return true;}
}
