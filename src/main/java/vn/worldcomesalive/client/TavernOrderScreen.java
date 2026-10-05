package vn.worldcomesalive.client;
import vn.worldcomesalive.server.InteractionPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Live stock and price snapshots; purchases use the same server session validation as dialogue. */
public final class TavernOrderScreen extends CitizenScreen {
    private String category="Drink";
    private int page;
    public TavernOrderScreen(InteractionPackets.View view){super(view);category=view.mode().equals("drinks")?"Drink":"Food";}
    @Override protected void init(){int w=Math.min(620,width-24),x=(width-w)/2;
        int i=0;for(String name:List.of("Food","Drink","Specials","Rooms")){int index=i++;addDrawableChild(ButtonWidget.builder(Text.literal(name),b->{category=name;page=0;clearAndInit();}).dimensions(x+12+index*(w-24)/4,78,(w-32)/4,20).build());}
        if(category.equals("Rooms")){if(view.options().contains("rent"))addDrawableChild(ButtonWidget.builder(Text.literal("View available rooms"),b->send("rent")).dimensions(x+12,128,w-24,24).build());}
        else{List<InteractionPackets.MenuRow> rows=view.menu().stream().filter(r->r.category().equals(category)).toList();int visible=Math.max(1,(height-230)/26);page=Math.min(page,Math.max(0,(rows.size()-1)/visible));for(int n=page*visible;n<Math.min(rows.size(),(page+1)*visible);n++){var row=rows.get(n);addDrawableChild(ButtonWidget.builder(Text.literal(row.name()+"   ·   "+row.price()+" crowns   ·   stock "+row.stock()),b->send((view.mode().equals("drinks")?"treat:":"order:")+row.id())).dimensions(x+12,125+(n%visible)*26,w-24,22).build());}if(rows.size()>visible){addDrawableChild(ButtonWidget.builder(Text.literal("Previous"),b->{page=Math.max(0,page-1);clearAndInit();}).dimensions(x+12,height-80,100,20).build());addDrawableChild(ButtonWidget.builder(Text.literal("Next"),b->{page++;clearAndInit();}).dimensions(x+w-112,height-80,100,20).build());}}
        addDrawableChild(ButtonWidget.builder(Text.literal("Back to conversation"),b->send("talk")).dimensions(x+12,height-48,w-24,20).build());
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){int w=Math.min(620,width-24),x=(width-w)/2;c.fill(x-4,20,x+w+4,height-15,0xed1b201c);c.fill(x-4,20,x+w+4,23,0xffba965b);c.drawTextWithShadow(textRenderer,view.mode().equals("drinks")?"BUY A DRINK FOR "+view.name().toUpperCase(Locale.ROOT):"TAVERN · "+view.settlement().toUpperCase(Locale.ROOT),x+12,34,0xffd7b477);c.drawTextWithShadow(textRenderer,"Crowns "+view.money()+"    Available kitchen / cellar stock",x+12,53,0xffeee4ca);int y=104;for(var line:textRenderer.wrapLines(Text.literal(view.text()),w-24)){if(y>117)break;c.drawTextWithShadow(textRenderer,line,x+12,y,0xffa3b4a3);y+=11;}for(var child:children())if(child instanceof ButtonWidget b)b.render(c,mx,my,delta);}
}
