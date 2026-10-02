package vn.svarcade.tcg.client.component;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vn.svarcade.tcg.client.CardWorldsScreen;
import vn.svarcade.tcg.fabric.TcgMod;
import vn.svarcade.tcg.economy.CardWorldsCurrency;

import java.util.UUID;

/** Dual-currency purchase controls. Visuals are a Card Worlds/resource-pack contract; balances stay server authoritative in BEconomy. */
public final class CurrencyPurchaseUi {
    public static ItemStack coin(String currency) {
        int cmd=switch(currency) {
            case CardWorldsCurrency.BEAST -> CardWorldsCurrency.BEAST_VISUAL.customModelData();
            case CardWorldsCurrency.HUNTER -> CardWorldsCurrency.HUNTER_VISUAL.customModelData();
            default -> throw new IllegalArgumentException("Unknown Card Worlds currency "+currency);
        };
        if(!CardWorldsCurrency.visual(currency).itemId().equals("minecraft:gold_ingot"))
            throw new IllegalStateException("Unsupported currency render item "+CardWorldsCurrency.visual(currency).itemId());
        ItemStack stack=new ItemStack(Items.GOLD_INGOT);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelDataComponent(cmd));
        return stack;
    }

    public static void render(CardWorldsScreen a,Ui u,TcgMod.BannerView selected,Rect info,int ix) {
        int width=info.w()-34;
        u.text("cardworlds.ui.pull_currency",ix,info.bottom()-126,12,Ui.MUTED);
        Rect beast=new Rect(ix,info.bottom()-106,width,34);
        Rect hunter=new Rect(ix,info.bottom()-66,width,34);
        u.button("150 Beast Coin",beast,true,true,()->pull(a,selected,BEconomyCardWorlds.BEAST));
        u.button("2 Hunter Coin",hunter,false,true,()->pull(a,selected,BEconomyCardWorlds.HUNTER));
        draw(u,coin(BEconomyCardWorlds.BEAST),new Rect(beast.x()+7,beast.y()+7,20,20));
        draw(u,coin(BEconomyCardWorlds.HUNTER),new Rect(hunter.x()+7,hunter.y()+7,20,20));
        u.text("25k = 1 Hunter Coin",ix,info.bottom()-25,11,Ui.GOLD);
    }

    private static void pull(CardWorldsScreen a,TcgMod.BannerView selected,String currency) {
        a.reveal.await(selected.previewCard(),selected.name());
        a.send("pull",selected.id(),currency+":"+UUID.randomUUID());
    }

    private static void draw(Ui u,ItemStack stack,Rect r) {
        u.c.getMatrices().push();
        u.c.getMatrices().translate(r.x(),r.y(),0);
        u.c.getMatrices().scale(r.w()/16f,r.h()/16f,1);
        u.c.drawItem(stack,0,0);
        u.c.getMatrices().pop();
    }

    private CurrencyPurchaseUi(){}
}
