package vn.worldcomesalive.civilization;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.function.BooleanSupplier;
/** Full ItemStack codecs preserve names, enchantments and mod components; never flatten belongings to item counts. */
public final class PersonalStorage {
    private record Open(UUID player,int sync){}
    private final Map<String,Open> locks=new HashMap<>();
    public static ItemStack decode(ServerPlayerEntity p,String json){return ItemStack.CODEC.parse(p.getRegistryManager().getOps(JsonOps.INSTANCE),JsonParser.parseString(json)).getOrThrow();}
    public static String encode(ServerPlayerEntity p,ItemStack stack){return ItemStack.CODEC.encodeStart(p.getRegistryManager().getOps(JsonOps.INSTANCE),stack).getOrThrow().toString();}
    public void open(ServerPlayerEntity p,String key,List<String> contents,String title,BooleanSupplier permission){var lock=locks.get(key);if(lock!=null){var owner=p.getServer().getPlayerManager().getPlayer(lock.player);if(owner!=null&&owner.currentScreenHandler.syncId==lock.sync){p.sendMessage(Text.literal("This storage is already in use."),true);return;}locks.remove(key);}
        SimpleInventory inventory=new SimpleInventory(Math.max(27,((contents.size()+8)/9)*9));if(inventory.size()>54){p.sendMessage(Text.literal("Collect belongings before storing more items."),true);return;}for(int i=0;i<contents.size();i++)inventory.setStack(i,decode(p,contents.get(i)));inventory.addListener(inv->{contents.clear();for(int i=0;i<inv.size();i++)if(!inv.getStack(i).isEmpty())contents.add(encode(p,inv.getStack(i)));});
        int rows=inventory.size()/9;ScreenHandlerType<GenericContainerScreenHandler> type=rows<=3?ScreenHandlerType.GENERIC_9X3:rows==4?ScreenHandlerType.GENERIC_9X4:rows==5?ScreenHandlerType.GENERIC_9X5:ScreenHandlerType.GENERIC_9X6;
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync,pi,actor)->new GenericContainerScreenHandler(type,sync,pi,inventory,rows){@Override public boolean canUse(net.minecraft.entity.player.PlayerEntity actor){return permission.getAsBoolean();}},Text.literal(title)));locks.put(key,new Open(p.getUuid(),p.currentScreenHandler.syncId));
    }
}
