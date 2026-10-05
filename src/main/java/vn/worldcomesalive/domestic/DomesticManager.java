package vn.worldcomesalive.domestic;

import vn.worldcomesalive.model.LivingWorld.*;
import vn.worldcomesalive.server.WorldSimulation;
import vn.worldcomesalive.furniture.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.screen.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.text.Text;
import java.util.*;

/** Event-driven production, pantry consumption, paid orders and dining share one persistent state. */
public final class DomesticManager {
    private record Due(long time,UUID id,boolean batch){}
    private record OpenStorage(UUID player,SimpleInventory inventory,int sync){}
    private final Map<String,OpenStorage> storage=new HashMap<>();
    private final WorldSimulation sim;
    private final PriorityQueue<Due> due=new PriorityQueue<>(Comparator.comparingLong(Due::time));
    public long mealsServed,mealsConsumed,drinksConsumed,productionCompleted;
    public DomesticManager(WorldSimulation simulation){sim=simulation;sim.state.domestic.batches.values().forEach(b->due.add(new Due(b.due,b.id,true)));sim.state.domestic.meals.values().forEach(m->due.add(new Due(m.due,m.id,false)));}
    public void initialize(Settlement s){
        if(!s.ready)return;Building farm=s.service("farm");if(farm==null)farm=s.buildings.values().iterator().next();
        for(Building b:s.buildings.values())if(!b.domesticInitialized){
            b.region=s.region;
            if(b==farm)DomesticContent.active.initialFarmResources.forEach((id,n)->b.stock.merge(id,n,Integer::sum));
            b.domesticInitialized=true;
            var h=s.households.values().stream().filter(v->v.home.equals(b.id)).findFirst().orElse(null);
            if(h!=null){h.wealth=b.wealth;for(UUID id:h.members){Npc n=sim.npc(id);int bread=n.inventory.getOrDefault("minecraft:bread",0);if(bread>0){h.supplies.merge("minecraft:bread",bread,Integer::sum);n.inventory.put("minecraft:bread",0);}}}
        }
        for(UUID id:s.residents)preferences(sim.npc(id),s);
        for(Building b:s.buildings.values())if(Set.of("tavern","bakery").contains(b.type))work(s,b,b.type.equals("tavern")?"innkeeper":"baker",.2);
    }
    public static void preferences(Npc n,Settlement s){if(!n.preferences.isEmpty())return;Random r=new Random(n.id.getLeastSignificantBits());n.alcoholTolerance=.6+n.trait("patience")*.8;n.preferences.put("alcohol",n.age<18||n.profession.equals("healer")?-1:n.trait("risk_tolerance")*.7);for(var e:DomesticContent.active.goods.entrySet()){var g=e.getValue();double value=r.nextDouble()*.8+.1;if(g.regions().contains(s.region))value+=.2;if(n.profession.equals("farmer")&&g.tags().contains("grain"))value+=.2;if(g.tags().contains("luxury"))value+=n.trait("ambition")*.25; if(g.alcohol()>0&&n.preferences.get("alcohol")<0)value=-1;n.preferences.put(e.getKey(),value);}}
    public void work(Settlement s,Building b,String role,double skill){
        if(b==null||sim.state.events.stream().anyMatch(e->e.settlement.equals(s.id)&&e.state.equals("active")&&e.type.equals("route_damage")))return;
        int jobs=0;for(var recipe:DomesticContent.active.recipes){
            if(!recipe.roles().contains(role)||b.stock.getOrDefault(recipe.output(),0)>=12)continue;
            // Imports are actual debits and payments; interrupted routes prevent resupply.
            for(var input:recipe.inputs().entrySet()){int missing=input.getValue()-b.stock.getOrDefault(input.getKey(),0);if(missing<=0)continue;Building supplier=s.buildings.values().stream().filter(x->x!=b&&x.stock.getOrDefault(input.getKey(),0)>=missing).findFirst().orElse(null);long price=missing;if(supplier==null||b.money<price)continue;supplier.stock.merge(input.getKey(),-missing,Integer::sum);b.stock.merge(input.getKey(),missing,Integer::sum);b.money-=price;supplier.money+=price;sim.state.transact(b.id,supplier.id,input.getKey(),missing,price,"domestic ingredient supply");}
            var batch=Production.reserve(sim.state.domestic,s,b,recipe,sim.state.clock,skill);if(batch!=null){due.add(new Due(batch.due,batch.id,true));if(++jobs>=8)break;}
        }
    }
    public void tick(){storage.entrySet().removeIf(e->{var p=sim.server.getPlayerManager().getPlayer(e.getValue().player);return p==null||p.currentScreenHandler.syncId!=e.getValue().sync;});
        int budget=0;while(!due.isEmpty()&&due.peek().time<=sim.state.clock&&budget++<24){Due event=due.remove();if(event.batch){var batch=sim.state.domestic.batches.remove(event.id);if(batch==null)continue;Settlement s=sim.state.settlements.get(batch.settlement);Building b=s==null?null:s.buildings.get(batch.building);if(b==null)continue;b.stock.merge(batch.output,batch.count,Integer::sum);b.quality.put(batch.output,batch.quality);productionCompleted++;sim.state.transact(b.id,b.id,batch.output,batch.count,0,"domestic production completed");work(s,b,b.type.equals("tavern")?"innkeeper":"baker",.2);}else{
            var m=sim.state.domestic.meals.get(event.id);if(m==null||m.due!=event.time)continue;
            if(m.state.equals("Prepared")){serve(m);}else if(m.state.equals("Served")){if(!m.player)consumeNpc(m);else {var p=sim.server.getPlayerManager().getPlayer(m.actor);if(p!=null){for(String id:List.of(m.food,m.drink))if(!id.isBlank()){ItemStack item=DomesticItems.stack(id,1);if(!p.getInventory().insertStack(item))p.dropItem(item,false);}}else {Building b=sim.state.settlements.get(m.settlement).buildings.get(m.building);for(String id:List.of(m.food,m.drink))if(!id.isBlank())b.stock.merge(id,1,Integer::sum);}}m.state="Dirty Tableware";m.due=sim.state.clock+200;visual(m);due.add(new Due(m.due,m.id,false));}else {m.state="Cleared";visual(m);sim.state.domestic.meals.remove(m.id);}
        }}
        if(sim.state.clock%100==0){var it=sim.state.domestic.cleanup.entrySet().iterator();while(it.hasNext()){Pos point=it.next().getValue();BlockPos p=BlockPos.ofFloored(point.x(),point.y()+1,point.z());if(sim.world.isChunkLoaded(p.getX()>>4,p.getZ()>>4)&&sim.state.domestic.meals.values().stream().noneMatch(m->m.table.equals(point))){if(sim.world.getBlockState(p).isOf(FurnitureRegistry.SETTING))sim.world.setBlockState(p,net.minecraft.block.Blocks.AIR.getDefaultState(),2);it.remove();}}for(var e:sim.visible()){Npc n=sim.npc(e.getUuid());DomesticState.Meal m=meal(n.id);if(m!=null)visual(m);double units=level(n);if(units>=DomesticContent.active.alcohol.drunk())n.emotion="tipsy";
            String prop=Set.of("harvesting field","planting field","tending field").contains(n.activity)?"minecraft:iron_hoe":n.activity.equals("serving customer")?"worldcomesalive:serving_tray":n.activity.equals("drinking")?(m==null?"worldcomesalive:wooden_mug":DomesticContent.active.good(m.drink).container()):n.activity.equals("dining")?"worldcomesalive:spoon":"";
            e.equipStack(EquipmentSlot.MAINHAND,prop.isBlank()?ItemStack.EMPTY:DomesticItems.stack(prop,1));
        }}
    }
    public double level(Npc n){n.intoxication=DomesticContent.active.sober(n.intoxication,n.intoxicationAt,sim.state.clock);n.intoxicationAt=sim.state.clock;return n.intoxication;}
    public double cardSkill(Npc n){return Math.max(.05,n.skills.getOrDefault("cards",.2)-Math.min(DomesticContent.active.alcohol.maxCardPenalty(),Math.max(0,level(n)-1.5)*.04));}
    public DomesticState.Meal meal(UUID actor){return sim.state.domestic.meals.values().stream().filter(m->m.actor.equals(actor)&&!m.state.equals("Cleared")).findFirst().orElse(null);}
    public boolean homeMeal(Npc n,Building home){
        if(meal(n.id)!=null)return true;Settlement s=sim.state.settlements.get(n.settlement);Household h=s.households.get(n.household);
        String food=h.supplies.entrySet().stream().filter(e->e.getValue()>0&&edible(e.getKey())).max(Comparator.comparingDouble(e->n.preferences.getOrDefault(e.getKey(),.5))).map(Map.Entry::getKey).orElse("");
        if(food.isBlank()){food=n.inventory.entrySet().stream().filter(e->e.getValue()>0&&edible(e.getKey())).map(Map.Entry::getKey).findFirst().orElse("");if(!food.isBlank())n.inventory.merge(food,-1,Integer::sum);}else {OpenStorage open=storage.get(home.id);if(open==null)h.supplies.merge(food,-1,Integer::sum);else {for(int slot=0;slot<open.inventory.size();slot++){var item=open.inventory.getStack(slot);if(!item.isEmpty()&&net.minecraft.registry.Registries.ITEM.getId(item.getItem()).toString().equals(food)){open.inventory.removeStack(slot,1);open.inventory.markDirty();break;}}}}
        if(food.isBlank())return false;start(n.id,s,home,food,"",false,true);return true;
    }
    private boolean edible(String id){var good=DomesticContent.active.good(id);return good!=null?good.nutrition()>0&&!good.category().equals("drink"):id.equals("minecraft:bread");}
    public void tavernVisit(Npc n,Settlement s,Building tavern){
        if(tavern==null||!tavern.contains(n.location)||meal(n.id)!=null)return;
        String food=n.need("hunger")>.15?choose(n,tavern,false):"";String drink=sim.state.clock-n.lastDrink>=DomesticContent.active.npcDrinkInterval?choose(n,tavern,true):"";
        long cost=(food.isBlank()?0:price(n,tavern,food))+(drink.isBlank()?0:price(n,tavern,drink));if(cost==0||n.money<cost)return;
        if(!food.isBlank())tavern.stock.merge(food,-1,Integer::sum);if(!drink.isBlank())tavern.stock.merge(drink,-1,Integer::sum);n.money-=cost;tavern.money+=cost;sim.state.transact(n.id.toString(),tavern.id,food.isBlank()?drink:food,1,cost,"tavern customer order");start(n.id,s,tavern,food,drink,false,true);n.lastDrink=sim.state.clock;n.activity=food.isBlank()?"drinking":"dining";
    }
    private long price(Npc n,Building b,String id){return DomesticContent.active.price(id,b.region,0);}
    private String choose(Npc n,Building b,boolean drink){return b.stock.entrySet().stream().filter(e->e.getValue()>0&&DomesticContent.active.good(e.getKey())!=null).filter(e->{var g=DomesticContent.active.good(e.getKey());return (drink?g.category().equals("drink"):edible(e.getKey()))&&n.preferences.getOrDefault(e.getKey(),.5)>0&&(g.alcohol()==0||(n.age>=18&&n.preferences.getOrDefault("alcohol",-1.0)>=0&&level(n)<1.5));}).max(Comparator.comparingDouble(e->n.preferences.getOrDefault(e.getKey(),.5)-price(n,b,e.getKey())*.015)).map(Map.Entry::getKey).orElse("");}
    public static boolean purchase(PlayerLife life,Building b,String id,long price){if(price<1||life.money<price||b.stock.getOrDefault(id,0)<1)return false;life.money-=price;b.money+=price;b.stock.merge(id,-1,Integer::sum);return true;}
    public String order(ServerPlayerEntity player,Npc host,String id,boolean treat){
        Settlement s=sim.state.settlements.get(host.settlement);Building b=s.service("tavern");var good=DomesticContent.active.good(id);if(b==null||good==null||(!good.category().equals("drink")&&good.nutrition()==0))return "That is not on today's menu.";
        if(treat&&(host.preferences.getOrDefault(id,.5)<=.1||(good.alcohol()>0&&(host.age<18||host.preferences.getOrDefault("alcohol",-1.0)<0))))return "Thank you, but I would prefer something else.";
        var life=sim.state.players.computeIfAbsent(player.getUuid(),k->new PlayerLife());long price=DomesticContent.active.price(id,s.region,host.relationship(player.getUuid()).trust);
        if(!purchase(life,b,id,price))return "We cannot fill that order: check the remaining stock and your crowns.";
        sim.state.transact(player.getUuidAsString(),b.id,id,1,price,treat?"buy drink for citizen":"tavern order");
        if(treat){applyDrink(host,id);var r=host.relationship(player.getUuid());r.friendship+=Math.min(2,host.preferences.getOrDefault(id,.5)*2);r.familiarity++;sim.state.remember(host,new Memory("shared_drink",player.getUuid(),b.id,sim.state.clock,.65,.4,1,"witnessed"));return "Thank you for the "+good.name()+". Let us share a moment.";}
        if(player.hasVehicle()&&player.getVehicle() instanceof SeatEntity&&b.contains(new Pos(player.getX(),player.getY(),player.getZ()))&&meal(player.getUuid())==null){start(player.getUuid(),s,b,good.category().equals("drink")?"":id,good.category().equals("drink")?id:"",true,false);return "Your order is being brought to your table. Use the table setting to enjoy it.";}
        ItemStack stack=DomesticItems.stack(id,1);if(!player.getInventory().insertStack(stack))player.dropItem(stack,false);return "Here is your "+good.name()+" · "+price+" crowns. Stock remaining: "+b.stock.getOrDefault(id,0)+".";
    }
    private void start(UUID actor,Settlement s,Building b,String food,String drink,boolean player,boolean immediate){
        var tables=b.markers.getOrDefault(Marker.DINING_TABLE,List.of(b.point(Marker.DINING_POINT)));Pos from=player?position(sim.server.getPlayerManager().getPlayer(actor)):sim.npc(actor).location;
        Pos table=tables.stream().min(Comparator.comparingDouble(t->t.distance(from))).orElse(null);
        if(table==null){if(player){var p=sim.server.getPlayerManager().getPlayer(actor);for(String id:List.of(food,drink))if(!id.isBlank()){ItemStack stack=DomesticItems.stack(id,1);if(!p.getInventory().insertStack(stack))p.dropItem(stack,false);}}else{if(!food.isBlank()){Npc n=sim.npc(actor);n.needs.put("hunger",Math.max(0,n.need("hunger")-.65));}if(!drink.isBlank())applyDrink(sim.npc(actor),drink);}return;}
        var m=new DomesticState.Meal();m.id=UUID.randomUUID();m.actor=actor;m.settlement=s.id;m.building=b.id;m.food=food;m.drink=drink;m.player=player;m.table=table;m.created=sim.state.clock;m.quality=b.quality.getOrDefault(food,1);m.due=sim.state.clock+(immediate?1:200);
        if(!immediate){Npc worker=s.residents.stream().map(sim::npc).filter(n->n.profession.equals("innkeeper")&&n.interruptUntil<=sim.state.clock&&n.servingOrder.isBlank()).findFirst().orElse(null);if(worker!=null){m.server=worker.id;worker.servingOrder=m.id.toString();worker.activity="serving customer";worker.plan.clear();worker.actionUntil=sim.state.clock+200;Travel t=new Travel();t.route=new ArrayList<>(List.of(worker.location,new Pos(table.x()+1,table.y(),table.z())));t.departure=sim.state.clock;t.destination=b.id;worker.travel=t;}}
        sim.state.domestic.meals.put(m.id,m);due.add(new Due(m.due,m.id,false));
    }
    private static Pos position(ServerPlayerEntity p){return new Pos(p.getX(),p.getY(),p.getZ());}
    private void serve(DomesticState.Meal m){if(m.server!=null&&sim.state.clock-m.created<400){Npc worker=sim.npc(m.server);if(worker!=null&&worker.location.distance(m.table)>3){m.due=sim.state.clock+40;due.add(new Due(m.due,m.id,false));return;}}m.state="Served";m.due=sim.state.clock+(m.player?1200:400);mealsServed++;if(m.server!=null){Npc worker=sim.npc(m.server);if(worker!=null){worker.servingOrder="";worker.activity="working";worker.travel=null;}}visual(m);due.add(new Due(m.due,m.id,false));}
    private void consumeNpc(DomesticState.Meal m){Npc n=sim.npc(m.actor);if(n==null)return;if(!m.food.isBlank()){n.needs.put("hunger",Math.max(0,n.need("hunger")-.65));m.foodConsumed=true;mealsConsumed++;}if(!m.drink.isBlank()){applyDrink(n,m.drink);m.drinkConsumed=true;}n.activity="socializing";}
    private void applyDrink(Npc n,String id){var good=DomesticContent.active.good(id);if(good==null)return;n.intoxication=level(n)+(DomesticContent.active.alcohol.enabled()?good.alcohol()/n.alcoholTolerance:0);n.intoxicationAt=sim.state.clock;n.lastDrink=sim.state.clock;drinksConsumed++;}
    public boolean consume(ServerPlayerEntity p,BlockPos pos){var meal=sim.state.domestic.meals.values().stream().filter(m->m.player&&m.actor.equals(p.getUuid())&&m.state.equals("Served")&&BlockPos.ofFloored(m.table.x(),m.table.y()+1,m.table.z()).equals(pos)).findFirst().orElse(null);if(meal==null||p.squaredDistanceTo(pos.toCenterPos())>16)return false;
        for(String id:List.of(meal.food,meal.drink))if(!id.isBlank()){var good=DomesticContent.active.good(id);p.getHungerManager().add(good.nutrition(),good.saturation());var life=sim.state.players.computeIfAbsent(p.getUuid(),k->new PlayerLife());DomesticItems.drink(life,good.alcohol(),sim.state.clock);if(good.category().equals("drink")){meal.drinkConsumed=true;drinksConsumed++;}else {meal.foodConsumed=true;mealsConsumed++;}}
        meal.state="Dirty Tableware";meal.due=sim.state.clock+200;visual(meal);due.add(new Due(meal.due,meal.id,false));return true;
    }
    private void visual(DomesticState.Meal m){if(m.table==null)return;BlockPos pos=BlockPos.ofFloored(m.table.x(),m.table.y()+1,m.table.z());if(!sim.world.isChunkLoaded(pos.getX()>>4,pos.getZ()>>4)){if(m.state.equals("Cleared"))sim.state.domestic.cleanup.put(m.table.toString(),m.table);return;}if(!sim.world.getBlockState(pos.down()).isIn(Semantics.DINING))return;var existing=sim.world.getBlockState(pos);if(!existing.isAir()&&!existing.isOf(FurnitureRegistry.SETTING)&&!existing.isIn(Semantics.TABLE_DISPLAY))return;
        if(m.state.equals("Cleared")){if(sim.state.domestic.meals.values().stream().anyMatch(other->!other.id.equals(m.id)&&other.table.equals(m.table)&&!other.state.equals("Cleared")))return;if(existing.isOf(FurnitureRegistry.SETTING))sim.world.setBlockState(pos,FurnitureRegistry.BLOCKS.get("idle_tableware").getDefaultState(),2);return;}if(m.state.equals("Prepared"))return;
        var course=m.state.equals("Dirty Tableware")?TableSettingBlock.Course.DIRTY:m.food.isBlank()?TableSettingBlock.Course.DRINK:m.drink.isBlank()?TableSettingBlock.Course.MEAL:TableSettingBlock.Course.BOTH;sim.world.setBlockState(pos,FurnitureRegistry.SETTING.getDefaultState().with(TableSettingBlock.COURSE,course),2);
    }

    public void claimContainer(ServerPlayerEntity p,BlockPos pos){String key=p.getWorld().getRegistryKey().getValue()+":"+pos.asLong();var container=sim.state.domestic.containers.computeIfAbsent(key,k->new DomesticState.Container());container.owner=p.getUuid();}
    public void breakContainer(net.minecraft.world.World world,BlockPos pos){String key=world.getRegistryKey().getValue()+":"+pos.asLong();var container=sim.state.domestic.containers.remove(key);if(container!=null)container.items.forEach((id,count)->{for(int left=count;left>0;left-=64)net.minecraft.block.Block.dropStack(world,pos,DomesticItems.stack(id,Math.min(64,left)));});storage.remove(key);}
    public void openStorage(ServerPlayerEntity p,Building b,BlockPos pos){
        String key;Map<String,Integer> stock;
        if(b==null){key=p.getWorld().getRegistryKey().getValue()+":"+pos.asLong();var c=sim.state.domestic.containers.computeIfAbsent(key,k->{var created=new DomesticState.Container();created.owner=p.getUuid();return created;});if(!p.getUuid().equals(c.owner)){p.sendMessage(Text.literal("This furniture belongs to another player."),true);return;}stock=c.items;}
        else{key=b.id;Settlement s=sim.state.settlements.values().stream().filter(x->x.buildings.containsKey(b.id)).findFirst().orElseThrow();var h=s.households.values().stream().filter(x->x.home.equals(b.id)).findFirst().orElse(null);var life=sim.state.players.computeIfAbsent(p.getUuid(),k->new PlayerLife());if(!life.property.contains(b.id)&&!b.owner.equals("player:"+p.getUuid())){p.sendMessage(Text.literal("This pantry belongs to "+(h==null?"the business":h.name)+". Buy a home or ask its household for permission."),true);return;}stock=h==null?b.stock:h.supplies;}
        if(storage.containsKey(key)){p.sendMessage(Text.literal("Someone is already using this storage."),true);return;}
        List<String> ids=stock.entrySet().stream().filter(e->e.getValue()>0).map(Map.Entry::getKey).limit(27).toList();SimpleInventory inventory=new SimpleInventory(27);for(int i=0;i<ids.size();i++)inventory.setStack(i,DomesticItems.stack(ids.get(i),Math.min(64,stock.get(ids.get(i)))));
        Map<String,Integer> initial=new HashMap<>();ids.forEach(id->initial.put(id,Math.min(64,stock.get(id))));
        inventory.addListener(inv->{Map<String,Integer> now=new HashMap<>();for(int i=0;i<inv.size();i++){ItemStack item=inv.getStack(i);if(!item.isEmpty())now.merge(net.minecraft.registry.Registries.ITEM.getId(item.getItem()).toString(),item.getCount(),Integer::sum);}Set<String> all=new HashSet<>(initial.keySet());all.addAll(now.keySet());for(String id:all){int delta=now.getOrDefault(id,0)-initial.getOrDefault(id,0);if(delta!=0)stock.merge(id,delta,Integer::sum);}initial.clear();initial.putAll(now);});
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync,playerInventory,player)->new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3,sync,playerInventory,inventory,3){@Override public boolean canUse(net.minecraft.entity.player.PlayerEntity player){return player.getWorld()==p.getWorld()&&player.squaredDistanceTo(pos.toCenterPos())<64;}},Text.literal("Domestic storage")));
        storage.put(key,new OpenStorage(p.getUuid(),inventory,p.currentScreenHandler.syncId));
    }
}
