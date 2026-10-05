package vn.worldcomesalive.client;
import vn.worldcomesalive.server.InteractionPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Dedicated business UI; server snapshot remains the source of stock, price and balance. */
public final class ShopScreen extends CitizenScreen {
    private int page;
    public ShopScreen(InteractionPackets.View view){super(view);}
    @Override protected void init(){int w=Math.min(700,width-24),x=(width-w)/2;boolean selling=view.mode().equals("shop_sell");addDrawableChild(ButtonWidget.builder(Text.literal(selling?"BUY":"SELL"),b->send(selling?"shop":"shop_sell")).dimensions(x+12,46,84,20).build());int visible=7;page=Math.min(page,Math.max(0,(view.menu().size()-1)/visible));int y=82;for(int i=page*visible;i<Math.min(view.menu().size(),(page+1)*visible);i++){var row=view.menu().get(i);String action=(selling?"shop_sell:":"shop_buy:")+row.id();String label=(selling?"Sell ":"Buy ")+row.name()+" · "+row.price()+" crowns · qty "+row.stock();addDrawableChild(ButtonWidget.builder(Text.literal(label),b->send(action)).dimensions(x+12,y,w-24,21).build());y+=23;}if(view.menu().size()>visible)addDrawableChild(ButtonWidget.builder(Text.literal("Next"),b->{page=(page+1)%((view.menu().size()+visible-1)/visible);clearAndInit();}).dimensions(x+w-96,46,84,20).build());addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->send("talk")).dimensions(x+12,height-35,w-24,20).build());}
    @Override public void render(DrawContext c,int mx,int my,float delta){int w=Math.min(700,width-24),x=(width-w)/2;c.fill(x-4,20,x+w+4,height-12,0xed171d19);c.fill(x-4,20,x+w+4,23,0xffb89052);c.drawTextWithShadow(textRenderer,(view.mode().equals("shop_sell")?"SELL TO ":"SHOP · ")+view.name(),x+12,31,0xffe1c184);c.drawTextWithShadow(textRenderer,view.settlement()+" · Crowns "+view.money(),x+112,51,0xffaab6a5);int y=66;for(var line:textRenderer.wrapLines(Text.literal(view.text()),w-130)){c.drawTextWithShadow(textRenderer,line,x+112,y,0xffeee4ca);y+=11;if(y>80)break;}for(var child:children())if(child instanceof ButtonWidget b)b.render(c,mx,my,delta);}
}
