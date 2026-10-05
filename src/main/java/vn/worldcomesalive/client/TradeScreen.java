package vn.worldcomesalive.client;
import vn.worldcomesalive.server.InteractionPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Direct character-to-character exchange. It intentionally does not reuse the business shop presentation. */
public final class TradeScreen extends CitizenScreen {
    private int page;
    public TradeScreen(InteractionPackets.View view){super(view);}
    @Override protected void init(){int w=Math.min(700,width-24),x=(width-w)/2,visible=7;page=Math.min(page,Math.max(0,(view.menu().size()-1)/visible));int y=82;for(int i=page*visible;i<Math.min(view.menu().size(),(page+1)*visible);i++){var row=view.menu().get(i);addDrawableChild(ButtonWidget.builder(Text.literal("Offer for "+row.name()+" · stock "+row.stock()),b->send("trade_buy:"+row.id())).dimensions(x+12,y,w-24,21).build());y+=23;}if(view.menu().size()>visible)addDrawableChild(ButtonWidget.builder(Text.literal("Next"),b->{page=(page+1)%((view.menu().size()+visible-1)/visible);clearAndInit();}).dimensions(x+w-96,46,84,20).build());addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->send("talk")).dimensions(x+12,height-35,w-24,20).build());}
    @Override public void render(DrawContext c,int mx,int my,float delta){int w=Math.min(700,width-24),x=(width-w)/2;c.fill(x-4,20,x+w+4,height-12,0xed1a1817);c.fill(x-4,20,x+w+4,23,0xffa87948);c.drawTextWithShadow(textRenderer,"DIRECT TRADE · "+view.name(),x+12,31,0xffe0bd84);c.drawTextWithShadow(textRenderer,"Your crowns: "+view.money()+" · "+view.text(),x+12,55,0xffddd2bd);for(var child:children())if(child instanceof ButtonWidget b)b.render(c,mx,my,delta);}
}
