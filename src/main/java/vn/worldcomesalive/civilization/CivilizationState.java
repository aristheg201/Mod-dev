package vn.worldcomesalive.civilization;
import vn.worldcomesalive.model.LivingWorld.Pos;
import java.util.*;
public final class CivilizationState {
    public Map<String,Government> governments=new LinkedHashMap<>();
    public Map<UUID,Contract> contracts=new LinkedHashMap<>();
    public Map<UUID,Property> properties=new LinkedHashMap<>();
    public Map<String,TradeNetwork.Route> tradeRoutes=new LinkedHashMap<>();
    public Map<UUID,TradeNetwork.Shipment> shipments=new LinkedHashMap<>();
    public static final class Government {public String type="COUNCIL";public UUID leader,deputy,playerLeader;public long treasury=250,lastTaxDay=-1;public double salesTax=.02;public Map<String,UUID> roles=new LinkedHashMap<>();public Map<String,Double> metrics=new LinkedHashMap<>();public List<String> laws=new ArrayList<>(List.of("Private rooms and storage require permission.","Witnessed theft and assault are prosecuted by the local watch.","Friendly Card Worlds and Pokémon challenges are permitted."));}
    public static final class Contract {public UUID id,requester,assignee;public String settlement,business,type="SUPPLY_RUN",title,description,item,knownInformation,locationQuality="EXACT",status="OFFERED";public Pos target;public int required,progress;public long reward,deadline;}
    public static final class Property {public UUID id,owner;public String settlement,building,type,occupancy="RESIDENT_HOUSEHOLD";public long value;public Set<String> permissions=new LinkedHashSet<>(),plots=new LinkedHashSet<>();public List<UUID> staff=new ArrayList<>();}
}
