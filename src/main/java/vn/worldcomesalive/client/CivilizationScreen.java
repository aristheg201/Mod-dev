package vn.worldcomesalive.client;
import vn.worldcomesalive.server.InteractionPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;
/** A status panel or journal backed only by the public, eligible server snapshot. */
public final class CivilizationScreen extends CitizenScreen {
    private int page;
    public CivilizationScreen(InteractionPackets.View view){super(view);}
    @Override protected void init(){int w=Math.min(620,width-24),x=(width-w)/2;var list=view.options().stream().filter(a->a.startsWith("details:")||a.startsWith("accept:")||a.startsWith("fulfill:")).toList();int visible=3;page=Math.min(page,Math.max(0,(list.size()-1)/visible));int y=height-120;for(int i=page*visible;i<Math.min(list.size(),(page+1)*visible);i++){String action=list.get(i),id=action.substring(action.indexOf(':')+1);var row=view.menu().stream().filter(r->r.id().equals(id)).findFirst().orElse(null);String label=action.startsWith("details:")?"Details":action.startsWith("accept:")?"Accept":"Deliver cargo";addDrawableChild(ButtonWidget.builder(Text.literal(label+" · "+(row==null?id:row.name())+(row==null?"":" · "+row.price()+" crowns")),b->send(action)).dimensions(x+12,y,w-24,21).build());y+=23;}if(list.size()>visible)addDrawableChild(ButtonWidget.builder(Text.literal("Next listings"),b->{page=(page+1)%((list.size()+visible-1)/visible);clearAndInit();}).dimensions(x+w-112,47,100,20).build());addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->send("talk")).dimensions(x+12,height-35,w-24,20).build());}
    @Override public void render(DrawContext c,int mx,int my,float delta){int w=Math.min(620,width-24),x=(width-w)/2;c.fill(x-4,20,x+w+4,height-12,0xed1b201c);c.fill(x-4,20,x+w+4,23,0xffba965b);String title=view.mode().equals("management")?"SETTLEMENT STATUS":view.mode().equals("journal")?"CONTRACT JOURNAL":"LOCAL REQUESTS";c.drawTextWithShadow(textRenderer,title+" · "+view.settlement(),x+12,33,0xffd7b477);int y=59;for(var line:textRenderer.wrapLines(Text.literal(view.text()),w-24)){if(y>height-(view.mode().equals("management")?48:133))break;c.drawTextWithShadow(textRenderer,line,x+12,y,0xffeee4ca);y+=11;}for(var child:children())if(child instanceof ButtonWidget b)b.render(c,mx,my,delta);}
}
