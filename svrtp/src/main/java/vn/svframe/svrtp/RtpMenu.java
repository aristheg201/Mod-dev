package vn.svframe.svrtp;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.network.chat.Component;
import java.util.*;

/** A vanilla generic chest screen; every inventory operation is rejected on the server. */
final class RtpMenu extends AbstractContainerMenu {
    private final ServerPlayer owner;
    private final SVRTP mod;
    private final Config config;
    private final String destination;
    private final SimpleContainer contents=new SimpleContainer(54);
    private boolean consumed;
    RtpMenu(int id,ServerPlayer player,SVRTP mod,String destination) {
        super(MenuType.GENERIC_9x6,id);owner=player;this.mod=mod;this.destination=destination;config=mod.config;
        for(int row=0;row<6;row++)for(int col=0;col<9;col++)addSlot(new Slot(contents,col+row*9,8+col*18,18+row*18));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player.getInventory(),9+row*9+col,8+col*18,140+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player.getInventory(),col,8+col*18,198));
        if(destination==null) {
            String[] aliases={"resource","nether","end"};Item[] icons={Items.GRASS_BLOCK,Items.NETHERRACK,Items.END_STONE};
            for(int i=0;i<3;i++)for(int row=1;row<=4;row++)for(int col=i*3;col<i*3+3;col++) {
                String alias=aliases[i];var d=config.destinations.get(alias);
                contents.setItem(row*9+col,item(icons[i],mod.text(alias.toUpperCase(),alias.toUpperCase()),List.of(
                        mod.text("Nhấn để chọn cách trả phí.","Click to choose payment."),
                        mod.text(config.prices.beastCoin+" BeastCoin hoặc "+config.prices.cobbleDollars+" CobbleDollars",config.prices.beastCoin+" BeastCoin or "+config.prices.cobbleDollars+" CobbleDollars"),
                        mod.text(d.dimension.isBlank()?"Dimension chưa được cấu hình.":"Điểm đến: "+d.dimension,d.dimension.isBlank()?"Dimension is not configured.":"Destination: "+d.dimension)),config.resourcePackGui));
            }
        } else {
            contents.setItem(20,item(Items.GOLD_INGOT,"BeastCoin",List.of(mod.text("Trả "+config.prices.beastCoin+" BeastCoin","Pay "+config.prices.beastCoin+" BeastCoin")),false));
            contents.setItem(24,item(Items.EMERALD,"CobbleDollars",List.of(mod.text("Trả "+config.prices.cobbleDollars+" CobbleDollars","Pay "+config.prices.cobbleDollars+" CobbleDollars")),false));
            contents.setItem(49,item(Items.ARROW,mod.text("Quay lại","Back"),List.of(),false));
        }
    }
    private static ItemStack item(Item icon,String title,List<String> lore,boolean transparent) {
        var stack=new ItemStack(transparent?Items.PAPER:icon);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(title));
        stack.set(DataComponents.LORE,new ItemLore(lore.stream().map(Component::literal).map(c->(Component)c).toList()));
        if(transparent)stack.set(DataComponents.CUSTOM_MODEL_DATA,new net.minecraft.world.item.component.CustomModelData(7032100));
        return stack;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        if(player!=owner || consumed || slot<0 || slot>=54 || type!=ClickType.PICKUP || button!=0)return;
        if(mod.config!=config) {consumed=true;owner.closeContainer();mod.tell(owner,"Cấu hình đã thay đổi. Hãy mở lại menu.","Configuration changed. Reopen the menu.");return;}
        if(destination==null && slot>=9 && slot<45) {
            consumed=true;mod.open(owner,new String[]{"resource","nether","end"}[(slot%9)/3]);
        } else if(destination!=null) {
            if(slot==49) {consumed=true;mod.open(owner,null);}
            else if(slot==20 || slot==24) {consumed=true;owner.closeContainer();mod.request(owner,destination,slot==20?"beastcoin":"cobbledollars");}
        }
    }
    @Override public ItemStack quickMoveStack(Player player,int slot) {return ItemStack.EMPTY;}
    @Override public boolean stillValid(Player player) {return player==owner && owner.isAlive();}
}
