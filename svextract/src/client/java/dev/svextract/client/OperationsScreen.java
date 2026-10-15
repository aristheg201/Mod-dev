package dev.svextract.client;

import com.google.gson.*;
import dev.svextract.network.Protocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import java.util.*;

/** Native Operations front end. The character is the actual equipped client player. */
public final class OperationsScreen extends Screen implements LobbyPanel {
    private static final Identifier EMBLEM=Identifier.of("svextract","textures/gui/operations_emblem.png");
    private static final Identifier HUB_BACKGROUND=Identifier.of("svextract","textures/gui/operations_hub.png");
    private static final String[] MODES={"operations","training","black_zone","battle_royale","versus","apex"};
    private JsonObject state;
    private String selected="operations";
    private int difficulty=1,bots=1,inviteIndex;
    private boolean acceptedRisk;
    private int left,right,top,row;
    private float rotation;
    private ButtonWidget start;
    private List<OrderedText> rules=List.of();
    public OperationsScreen(JsonObject state){super(Text.translatable("svextract.lobby.title"));this.state=state;}
    public String selectedMode(){return selected;}
    @Override public void updateLobby(JsonObject state){update(state);}
    public void update(JsonObject state){this.state=state;clearAndInit();}
    private static String string(JsonObject object,String key,String fallback){return object.has(key)&&object.get(key).isJsonPrimitive()?object.get(key).getAsString():fallback;}
    private boolean flag(String key){return state.has(key)&&state.get(key).getAsBoolean();}
    private JsonObject mode(){
        if(state.has("modes"))for(JsonElement e:state.getAsJsonArray("modes"))if(e.isJsonObject()&&selected.equals(string(e.getAsJsonObject(),"id","")))return e.getAsJsonObject();
        return new JsonObject();
    }
    private void request(int action,String target){ClientPlayNetworking.send(new Protocol.LobbyAction(action,selected,target,difficulty,bots,acceptedRisk));}
    private boolean prepared(){return flag("prepared")&&state.has("readyMaps")&&state.getAsJsonArray("readyMaps").asList().stream().anyMatch(e->e.getAsString().equals(string(mode(),"map","")));}
    @Override protected void init(){
        left=Math.max(110,Math.min(178,width/4));right=Math.max(116,Math.min(230,width/4));top=42;
        row=Math.max(12,Math.min(27,(height-140)/6));
        for(int i=0;i<MODES.length;i++){
            String id=MODES[i];
            addDrawableChild(new MenuButton(10,top+i*(row+2),left-16,row,Text.translatable("svextract.mode."+id),()->{selected=id;if(id.equals("versus"))difficulty=0;acceptedRisk=false;clearAndInit();},id.equals(selected)));
        }
        int rx=width-right+8,buttonWidth=right-18;
        int bottom=height-50;
        addDrawableChild(new MenuButton(rx,bottom-44,buttonWidth,18,Text.translatable(selected.equals("versus")?"svextract.versus.mode."+difficulty:"svextract.lobby.difficulty."+difficulty),()->{difficulty=(difficulty+1)%3;clearAndInit();},false));
        if(!selected.equals("versus"))addDrawableChild(new MenuButton(rx,bottom-23,buttonWidth,18,Text.translatable("svextract.lobby.bots",bots),()->{bots=(bots+1)%3;clearAndInit();},false));
        JsonObject definition=mode();
        boolean risk=definition.has("equipmentRisk")&&definition.get("equipmentRisk").getAsBoolean();
        if(risk)addDrawableChild(new MenuButton(rx,bottom-65,buttonWidth,18,Text.translatable(acceptedRisk?"svextract.lobby.risk_accepted":"svextract.lobby.accept_risk"),()->{acceptedRisk=!acceptedRisk;clearAndInit();},acceptedRisk));
        boolean enabled=definition.has("enabled")&&definition.get("enabled").getAsBoolean();
        String label=!enabled?"svextract.lobby.unavailable":!flag("atHub")?"svextract.lobby.return":!prepared()?"svextract.lobby.preparing":"svextract.lobby.deploy";
        start=addDrawableChild(new MenuButton(rx,bottom,buttonWidth,34,Text.translatable(label),()->{start.active=false;request(1,"");},true));
        start.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("svextract.rules."+selected)));
        start.active=enabled&&flag("atHub")&&prepared()&&!flag("locked")&&flag("pointBlank")&&(!risk||acceptedRisk);
        addDrawableChild(new MenuButton(10,height-62,left-16,20,Text.translatable("svextract.lobby.squad"),()->{if(client!=null)client.setScreen(new SquadScreen());},false));
        addDrawableChild(new MenuButton(10,height-36,left-16,23,Text.translatable("svextract.lobby.refresh"),()->request(0,""),false));
        int middleWidth=width-left-right;
        if(middleWidth>=96){
            addDrawableChild(new MenuButton(left+6,height-36,middleWidth/2-8,23,Text.translatable("svextract.stash.title"),()->ClientPlayNetworking.send(new Protocol.StashAction(0,0,0,0)),false));
            addDrawableChild(new MenuButton(left+middleWidth/2,height-36,middleWidth/2-8,23,Text.translatable("svextract.worlds.title"),()->{if(client!=null)client.setScreen(new WorldScreen());},false));
        }
        rules=textRenderer.wrapLines(Text.translatable("svextract.rules."+selected),right-20);
    }
    private String inviteName(){
        JsonArray online=state.has("online")?state.getAsJsonArray("online"):new JsonArray();
        return online.isEmpty()?Text.translatable("svextract.lobby.no_players").getString():online.get(inviteIndex%online.size()).getAsJsonObject().get("name").getAsString();
    }
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        // Original bundled backdrop; an actual equipped player is rendered over it below.
        // Scaling the matrix maps the full 1024x512 artwork to the current GUI resolution.
        c.getMatrices().push();c.getMatrices().scale(width/1024.0f,height/512.0f,1.0f);
        c.drawTexture(HUB_BACKGROUND,0,0,0,0,1024,512,1024,512);c.getMatrices().pop();
        c.fillGradient(0,0,width,height,0x4108161D,0xAE07121A);
        c.fill(0,0,width,35,0xE909141C);c.fill(0,35,width,37,0xFFF2BB46);
        c.fill(0,38,left,height,0xE50A1923);c.fill(width-right,38,width,height,0xE50A1923);
        c.fill(left,38,left+1,height,0x663B8293);c.fill(width-right-1,38,width-right,height,0x663B8293);
        c.fill(left,height-69,width-right,height,0xB50B1D25);
        c.fill(left,height-69,width-right,height-67,0xFFB08C46);
        c.drawTextWithShadow(textRenderer,Text.literal("OPERATOR // DEPLOYMENT READY"),left+10,45,0xC5C9B2);
        c.drawTextWithShadow(textRenderer,Text.literal("HAVEN-9  /  OPERATIONS TERMINAL"),left+10,height-79,0xAFC9CF);
        c.drawTexture(EMBLEM,7,2,0,0,32,32,32,32);
        c.drawTextWithShadow(textRenderer,Text.literal("SVEXTRACT / FRACTURE PROTOCOL"),43,12,0xE4F8F6);
        if(state.has("balances")&&width>=580){
            String balances=String.join("  •  ",state.getAsJsonArray("balances").asList().stream().map(JsonElement::getAsString).toList());
            c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(balances,width/2),width/2,23,0xC8D8DE);
        }
        if(client!=null&&client.player!=null&&width-left-right>40){
            int center=(left+width-right)/2;
            InventoryScreen.drawEntity(c,left+6,40,width-right-6,height-68,Math.max(24,Math.min(100,(height-94)/2)),0.0625f,
                center+rotation,height/2f,client.player);
            c.drawCenteredTextWithShadow(textRenderer,Text.literal(string(state,"player","Extractor")),center,height-61,0xE4F8F6);
            if(state.has("profile")){
                JsonObject profile=state.getAsJsonObject("profile");
                c.drawCenteredTextWithShadow(textRenderer,Text.literal(string(profile,"class","")+"  /  Lv "+string(profile,"level","0")),center,height-48,0x48D5C5);
            }
        }
        int rx=width-right+10,y=48;
        c.drawTextWithShadow(textRenderer,Text.translatable("svextract.lobby.operations"),rx,y,0x48D5C5);y+=16;
        c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(Text.translatable("svextract.mode."+selected).getString(),right-20),rx,y,0xFFFFFF);y+=15;
        c.drawText(textRenderer,textRenderer.trimToWidth(Text.translatable("svextract.map."+string(mode(),"map","redline_station")).getString(),right-20),rx,y,0xA7BDCB,false);y+=18;
        if(mode().has("equipmentRisk")&&mode().get("equipmentRisk").getAsBoolean()){c.drawText(textRenderer,textRenderer.trimToWidth(Text.translatable("svextract.lobby.risk_warning").getString(),right-20),rx,y,0xFF9988,false);y+=12;}
        int rulesEnd=height-128-(mode().has("equipmentRisk")&&mode().get("equipmentRisk").getAsBoolean()?20:0);
        for(OrderedText line:rules){if(y+10>rulesEnd)break;c.drawText(textRenderer,line,rx,y,0xC8D8DE,false);y+=11;}
        if(height>=300){
            y+=10;c.drawTextWithShadow(textRenderer,Text.translatable("svextract.lobby.extractions",string(state,"extractions","0")),rx,y,0xE4F8F6);y+=13;
            c.drawTextWithShadow(textRenderer,Text.translatable("svextract.lobby.reputation",string(state,"reputation","0")),rx,y,0xA7BDCB);
        }
        if(!string(state,"invite","").isEmpty()&&height>=300)c.drawText(textRenderer,textRenderer.trimToWidth(Text.translatable("svextract.lobby.invited",string(state,"invite","")).getString(),left-20),10,height-63,0xFFD66F,false);
        super.render(c,mouseX,mouseY,delta);
    }
    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double dx,double dy){
        if(button==0&&mouseX>left&&mouseX<width-right){rotation=(float)Math.max(-1000,Math.min(1000,rotation+dx*2));return true;}
        return super.mouseDragged(mouseX,mouseY,button,dx,dy);
    }
    @Override public void close(){request(8,"");super.close();}
    @Override public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){/* This front end draws its own background. */}
    @Override protected void applyBlur(float delta){/* Keep the equipped character and mission text legible. */}
    private final class SquadScreen extends Screen implements LobbyPanel {
        @Override public void updateLobby(JsonObject data){state=data;clearAndInit();}
        SquadScreen(){super(Text.translatable("svextract.lobby.squad"));}
        @Override protected void init(){
            int x=width/2-100,y=height/2;
            addDrawableChild(ButtonWidget.builder(Text.translatable("svextract.lobby.invite",inviteName()),b->{JsonArray online=state.getAsJsonArray("online");if(!online.isEmpty())request(2,online.get(inviteIndex%online.size()).getAsJsonObject().get("id").getAsString());}).dimensions(x,y,200,20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("svextract.lobby.next"),b->{inviteIndex++;clearAndInit();}).dimensions(x,y+24,96,20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("svextract.lobby.accept"),b->request(3,"")).dimensions(x+104,y+24,96,20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("svextract.lobby.leave"),b->request(4,"")).dimensions(x,y+48,200,20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"),b->close()).dimensions(x,y+72,200,20).build());
        }
        @Override public void render(DrawContext c,int mx,int my,float delta){
            renderBackground(c,mx,my,delta);super.render(c,mx,my,delta);
            c.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-68,0x48D5C5);
            if(state.has("squad"))c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(String.join(", ",state.getAsJsonArray("squad").asList().stream().map(JsonElement::getAsString).toList()),width-24),width/2,height/2-48,0xFFFFFF);
            c.drawCenteredTextWithShadow(textRenderer,Text.translatable("svextract.lobby.private_only"),width/2,height/2-28,0xFFD66F);
        }
        @Override public void close(){if(client!=null)client.setScreen(OperationsScreen.this);}
        @Override public boolean shouldPause(){return false;}
    }
    public final class WorldScreen extends Screen implements LobbyPanel {
        WorldScreen(){super(Text.translatable("svextract.worlds.title"));}
        @Override public void updateLobby(JsonObject data){state=data;clearAndInit();}
        @Override protected void init(){
            int y=height/2-35;
            if(state.has("worlds"))for(JsonElement entry:state.getAsJsonArray("worlds")){
                JsonObject world=entry.getAsJsonObject();String id=world.get("id").getAsString();boolean ready=world.get("prepared").getAsBoolean();
                var button=addDrawableChild(ButtonWidget.builder(Text.translatable("svextract.worlds."+id),b->request(id.equals("skyrim")?6:7,"")).dimensions(width/2-125,y,250,23).build());
                button.active=ready&&!flag("locked");
                button.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable(!world.get("installed").getAsBoolean()?"svextract.worlds.not_installed":ready?"svextract.worlds.travel":"svextract.worlds.preparing")));y+=29;
            }
            addDrawableChild(ButtonWidget.builder(Text.translatable("svextract.lobby.refresh"),b->request(0,"")).dimensions(width/2-125,y+10,120,20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"),b->close()).dimensions(width/2+5,y+10,120,20).build());
        }
        @Override public void render(DrawContext c,int mx,int my,float delta){
            c.fill(0,0,width,height,0xFF0D1822);c.fill(0,0,width,32,0xFF17343F);c.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0x48D5C5);
            c.drawCenteredTextWithShadow(textRenderer,Text.translatable("svextract.worlds.protected"),width/2,height/2-60,0xBDC8D7);super.render(c,mx,my,delta);
        }
        @Override public void close(){if(client!=null)client.setScreen(OperationsScreen.this);}
        @Override public boolean shouldPause(){return false;}
        @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
        @Override protected void applyBlur(float delta){}
    }
    private final class MenuButton extends ButtonWidget {
        private final boolean accent;
        MenuButton(int x,int y,int w,int h,Text text,Runnable action,boolean accent){super(x,y,w,h,text,b->action.run(),DEFAULT_NARRATION_SUPPLIER);this.accent=accent;}
        @Override protected void renderWidget(DrawContext c,int mouseX,int mouseY,float delta){
            int color=!active?0xFF27333D:isHovered()?0xFF2A646A:accent?0xFF207B75:0xFF1D303D;
            c.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),color);
            c.fill(getX(),getY()+getHeight()-1,getX()+getWidth(),getY()+getHeight(),active?0x99D1B76A:0x55273845);
            c.fill(getX(),getY(),getX()+3,getY()+getHeight(),active?accent?0xFFEAC055:0xFF658596:0xFF48515A);
            c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(getMessage().getString(),getWidth()-10),getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?0xE8F6F6:0x81919A);
        }
    }
}
