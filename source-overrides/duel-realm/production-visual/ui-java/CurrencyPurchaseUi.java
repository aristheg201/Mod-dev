package vn.svarcade.tcg.client.component;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vn.svarcade.tcg.client.CardWorldsScreen;
import vn.svarcade.tcg.fabric.TcgMod;
import vn.svarcade.tcg.economy.CardWorldsCurrency;

import java.util.UUID;

/** Dual-currency purchase controls. ItemStack visuals follow the server resource-pack contract. */
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
        u.text("Choose payment currency",ix,info.bottom()-126,12,Ui.MUTED);
        Rect beast=new Rect(ix,info.bottom()-106,width,34);
        Rect hunter=new Rect(ix,info.bottom()-66,width,34);
        u.button("150 Beast Coin",beast,true,true,()->pull(a,selected,CardWorldsCurrency.BEAST));
        u.button("2 Hunter Coin",hunter,false,true,()->pull(a,selected,CardWorldsCurrency.HUNTER));
        draw(u,coin(CardWorldsCurrency.BEAST),new Rect(beast.x()+7,beast.y()+7,20,20));
        draw(u,coin(CardWorldsCurrency.HUNTER),new Rect(hunter.x()+7,hunter.y()+7,20,20));
        u.text("25k = 1 Hunter Coin",ix,info.bottom()-25,11,Ui.GOLD);
    }

    /** Both header icons use the same ItemStacks as the purchase buttons, including CMD 6 / CMD 2. */
    public static void renderBalances(Ui u,CardWorldsCurrency.Balances balances) {
        boolean available=balances!=null&&balances.available();
        balance(u,CardWorldsCurrency.BEAST,"Beast Coin",available?balances.beast():null,748);
        balance(u,CardWorldsCurrency.HUNTER,"Hunter Coin",available?balances.hunter():null,902);
    }

    private static void balance(Ui u,String currency,String label,java.math.BigDecimal value,int x) {
        draw(u,coin(currency),new Rect(x,16,23,23));
        u.text(label,x+31,9,12,Ui.GOLD);
        String amount=value==null?"—":java.text.NumberFormat.getNumberInstance(java.util.Locale.ROOT).format(value);
        int size=16;
        while(size>8&&u.textWidth(amount,size)>110)size--;
        u.text(amount,x+31,27,size,Ui.WHITE);
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
