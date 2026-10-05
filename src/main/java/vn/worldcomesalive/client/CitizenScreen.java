package vn.worldcomesalive.client;
import vn.worldcomesalive.server.InteractionPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Presentation sees only observable dialogue/relationships; hidden memories never leave the server. */
public final class CitizenScreen extends Screen {
    public InteractionPackets.View view;
    private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("greet","Greet"),Map.entry("talk","Talk"),Map.entry("ask","Ask about the settlement"),Map.entry("gift","Give held gift"),Map.entry("trade","Buy bread"),Map.entry("deposit","Exchange emerald"),Map.entry("help","Help supply bakery"),Map.entry("invite","Invite to tavern"),Map.entry("flirt","Flirt"),Map.entry("propose","Propose marriage"),Map.entry("cards","Challenge to Card Duel"),Map.entry("rent","Rent a room · 10 crowns"),Map.entry("buy_home","Buy cottage · 300 crowns"),Map.entry("threaten","Threaten"));
    public CitizenScreen(InteractionPackets.View view){super(Text.literal("World Comes Alive"));this.view=view;}
    public void update(InteractionPackets.View next){view=next;clearAndInit();}
    @Override protected void init(){int w=Math.min(620,width-24),x=(width-w)/2,y=height/2-10;int columns=2,buttonWidth=(w-36)/2;for(int i=0;i<view.options().size();i++){String action=view.options().get(i);addDrawableChild(ButtonWidget.builder(Text.literal(LABELS.getOrDefault(action,action)),b->send(action)).dimensions(x+12+(i%columns)*(buttonWidth+12),y+(i/columns)*24,buttonWidth,20).build());}addDrawableChild(ButtonWidget.builder(Text.literal("Leave conversation"),b->close()).dimensions(x+w/2-90,height-32,180,20).build());}
    public void send(String action){ClientPlayNetworking.send(new InteractionPackets.Input(view.npc(),view.token(),view.revision(),action));}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){int w=Math.min(620,width-24),x=(width-w)/2;context.fill(x-4,20,x+w+4,height-15,0xe61b201c);context.fill(x-4,20,x+w+4,23,0xffba965b);context.drawTextWithShadow(textRenderer,"WORLD COMES ALIVE",x+12,31,0xffc5a568);context.drawTextWithShadow(textRenderer,view.name()+" · "+view.profession(),x+12,49,0xfff3ead5);context.drawTextWithShadow(textRenderer,view.settlement()+" | "+view.activity()+" | "+view.mood(),x+12,64,0xffa3b4a3);int y=84;for(var line:textRenderer.wrapLines(Text.literal(view.text()),w-28)){context.drawTextWithShadow(textRenderer,line,x+12,y,0xffeee4ca);y+=11;}context.drawTextWithShadow(textRenderer,"Friendship "+(int)view.friendship()+"   Trust "+(int)view.trust()+"   Respect "+(int)view.respect()+"   "+view.stage(),x+12,height/2-51,0xffd7b477);context.drawTextWithShadow(textRenderer,view.home(),x+12,height/2-37,0xffa3b4a3);context.drawTextWithShadow(textRenderer,"Crowns "+view.money()+(view.partner().isBlank()?"":"   Partner: "+view.partner()),x+12,height/2-23,0xffa3b4a3);super.render(context,mouseX,mouseY,delta);}
    @Override public void close(){send("close");super.close();}
    @Override public boolean shouldPause(){return false;}
}
