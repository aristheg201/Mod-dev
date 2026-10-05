package vn.worldcomesalive.civilization;
import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.world.SettlementBootstrap;
import java.util.*;
/** Inn rooms have exact generated bounds; beds remain separate from permanent residential capacity. */
public final class Lodging {
    public static final class Room {
        public UUID id,guest;public String settlement,building,name,quality,state="AVAILABLE";public Pos min,max,entrance,storage;public List<Pos> beds=new ArrayList<>();public List<String> contents=new ArrayList<>();public long price,until,cleanAt;
        public boolean contains(Pos p){return p.x()>=min.x()&&p.x()<max.x()&&p.y()>=min.y()&&p.y()<max.y()&&p.z()>=min.z()&&p.z()<max.z();}
        public boolean permitted(UUID actor,long clock){return actor.equals(guest)&&state.equals("RENTED")&&clock<until;}
    }
    public static List<Room> rooms(Settlement s){Building b=s.service("tavern");if(b==null||b.width<17||b.depth<17)return List.of();List<Room> result=new ArrayList<>();for(int i=0;i<2;i++){Room r=new Room();r.id=SettlementBootstrap.uuid(b.id+"guest_room_"+i);r.settlement=s.id;r.building=b.id;r.name=i==0?"Hearthside Room":"Merchant's Suite";r.quality=i==0?"Standard":"Comfort";r.price=i==0?12:24;int x=i==0?1:10;r.min=new Pos(b.origin.x()+x,b.origin.y()+5,b.origin.z()+3);r.max=new Pos(b.origin.x()+x+(i==0?7:6),b.origin.y()+8,b.origin.z()+7);r.entrance=new Pos(b.origin.x()+(i==0?7:10),b.origin.y()+5,b.origin.z()+5);r.storage=new Pos(b.origin.x()+x+(i==0?1:4),b.origin.y()+5,b.origin.z()+6);r.beds.add(new Pos(b.origin.x()+x+2,b.origin.y()+5,b.origin.z()+4));if(i==1)r.beds.add(new Pos(b.origin.x()+x+3,b.origin.y()+5,b.origin.z()+4));result.add(r);}return result;}
    public static boolean rent(Room r,UUID guest,PlayerLife wallet,Building inn,long now){if(!r.state.equals("AVAILABLE")||wallet.money<r.price)return false;wallet.money-=r.price;inn.money+=r.price;r.guest=guest;r.state="RENTED";r.until=now+24000;return true;}
    public static boolean renew(Room r,UUID actor,PlayerLife wallet,Building inn,long now){if(!r.permitted(actor,now)||wallet.money<r.price)return false;wallet.money-=r.price;inn.money+=r.price;r.until+=24000;return true;}
    public static void checkout(Room r,PlayerLife guest,long now){guest.recovery.addAll(r.contents);r.contents.clear();r.guest=null;r.until=0;r.state="DIRTY";r.cleanAt=now+400;}
    private Lodging(){}
}
