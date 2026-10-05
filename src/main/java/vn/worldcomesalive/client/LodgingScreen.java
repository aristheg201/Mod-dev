package vn.worldcomesalive.client;
import vn.worldcomesalive.server.InteractionPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
public final class LodgingScreen extends CitizenScreen {
    public LodgingScreen(InteractionPackets.View view){super(view);}
    @Override protected void init(){int w=Math.min(620,width-24),x=(width-w)/2,y=130;for(var room:view.menu())if(room.category().equals("Rooms")){String action=view.options().contains("rent:"+room.id())?"rent:":view.options().contains("renew:"+room.id())?"renew:":null;if(action!=null){String a=action+room.id();addDrawableChild(ButtonWidget.builder(Text.literal((action.equals("rent:")?"Rent":"Renew")+" · "+room.name()+" · "+room.price()+" crowns / night"),b->send(a)).dimensions(x+12,y,w-24,22).build());}if(view.options().contains("checkout:"+room.id())){String a="checkout:"+room.id();addDrawableChild(ButtonWidget.builder(Text.literal("Checkout · "+room.name()),b->send(a)).dimensions(x+12,y+24,w-24,22).build());y+=24;}y+=34;}
        addDrawableChild(ButtonWidget.builder(Text.literal("Recover stored belongings"),b->send("recover")).dimensions(x+12,height-75,w-24,22).build());addDrawableChild(ButtonWidget.builder(Text.literal("Back to conversation"),b->send("talk")).dimensions(x+12,height-46,w-24,22).build());}
    @Override public void render(DrawContext c,int mx,int my,float delta){int w=Math.min(620,width-24),x=(width-w)/2;c.fill(x-4,20,x+w+4,height-15,0xed1b201c);c.fill(x-4,20,x+w+4,23,0xffba965b);c.drawTextWithShadow(textRenderer,"INN LODGING · "+view.settlement(),x+12,34,0xffd7b477);c.drawTextWithShadow(textRenderer,"Crowns "+view.money()+" · private bed, storage and furniture · one night",x+12,55,0xffeee4ca);int y=76;for(var line:textRenderer.wrapLines(Text.literal(view.text()),w-24)){if(y>120)break;c.drawTextWithShadow(textRenderer,line,x+12,y,0xffa3b4a3);y+=11;}for(var child:children())if(child instanceof ButtonWidget b)b.render(c,mx,my,delta);}
}
