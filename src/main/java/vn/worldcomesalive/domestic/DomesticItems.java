package vn.worldcomesalive.domestic;

import net.minecraft.item.*;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.world.World;
import vn.worldcomesalive.model.LivingWorld.PlayerLife;
import vn.worldcomesalive.server.WorldSimulation;
import java.util.*;

/** Stable registry IDs; all nutrition, recipes, prices and preferences share the domestic catalog. */
public final class DomesticItems {
    public static final Map<String,Item> ITEMS=new LinkedHashMap<>();
    public static void initialize(){for(var entry:DomesticContent.active.goods.entrySet()){
        String id=entry.getKey();var good=entry.getValue();if(!id.startsWith("worldcomesalive:"))continue;
        if(Registries.ITEM.containsId(Identifier.of(id))){ITEMS.put(id,Registries.ITEM.get(Identifier.of(id)));continue;}
        Item.Settings settings=new Item.Settings();if(good.nutrition()>0||good.category().equals("drink"))settings.food(new FoodComponent.Builder().nutrition(good.nutrition()).saturationModifier(good.saturation()).alwaysEdible().build());
        Item item=new DomesticItem(id,settings);Registry.register(Registries.ITEM,Identifier.of(id),item);ITEMS.put(id,item);
    }net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(e->ITEMS.values().forEach(e::add));}
    public static ItemStack stack(String id,int count){return new ItemStack(Registries.ITEM.get(Identifier.of(id)),count);}
    public static void drink(PlayerLife life,double units,long clock){var c=DomesticContent.active;life.intoxication=c.sober(life.intoxication,life.intoxicationAt,clock)+(c.alcohol.enabled()?units:0);life.intoxicationAt=clock;}
    public static final class DomesticItem extends Item {
        private final String id;
        DomesticItem(String id,Settings settings){super(settings);this.id=id;}
        @Override public UseAction getUseAction(ItemStack stack){var good=DomesticContent.active.good(id);return good!=null&&good.category().equals("drink")?UseAction.DRINK:super.getUseAction(stack);}
        @Override public ItemStack finishUsing(ItemStack stack,World world,LivingEntity consumer){
            var good=DomesticContent.active.good(id);ItemStack result=super.finishUsing(stack,world,consumer);
            if(!world.isClient&&consumer instanceof ServerPlayerEntity player&&good!=null){var sim=WorldSimulation.active();if(sim!=null){var life=sim.state.players.computeIfAbsent(player.getUuid(),k->new PlayerLife());drink(life,good.alcohol(),sim.state.clock);life.skills.merge("cooking",.001,Double::sum);}
                if(!good.container().isBlank()){ItemStack container=stack(good.container(),1);if(result.isEmpty())return container;if(!player.getInventory().insertStack(container))player.dropItem(container,false);}
            }return result;
        }
    }
    private DomesticItems(){}
}
