package dev.svextract.client;

import dev.svextract.network.Protocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.nio.file.*;

public final class SvExtractClient implements ClientModInitializer {
    private static Protocol.Hud hud;
    public static Protocol.Hud currentHud(){return hud;}
    private static final class Settings {
        double scale=1.0;
        int x=10,y=10;
        boolean visible=true;
    }
    private Settings settings=new Settings();
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(dev.svextract.Content.COMBAT_BOT,net.minecraft.client.render.entity.PillagerEntityRenderer::new);
        Path path=FabricLoader.getInstance().getConfigDir().resolve("svextract/client.json");
        try{
            com.google.gson.Gson gson=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            if(Files.exists(path)){
                Settings read=gson.fromJson(Files.readString(path),Settings.class);
                if(read!=null&&Double.isFinite(read.scale)&&read.scale>=0.5&&read.scale<=2&&read.x>=0&&read.y>=0)settings=read;
            }else{Files.createDirectories(path.getParent());Files.writeString(path,gson.toJson(settings));}
        }catch(Exception e){dev.svextract.SvExtract.LOGGER.warn("Invalid client configuration; using defaults",e);}
        ClientPlayNetworking.registerGlobalReceiver(Protocol.Hello.ID,(hello,context)->context.responseSender().sendPacket(Protocol.hello()));
        ClientPlayNetworking.registerGlobalReceiver(Protocol.Hud.ID,(state,context)->{
            hud=state.phase().isEmpty()?null:state;
            if(hud!=null&&!hud.phase().equals("SETTLING")&&context.client().currentScreen instanceof OperationsScreen)context.client().setScreen(null);
        });
        ClientPlayNetworking.registerGlobalReceiver(Protocol.Result.ID,(result,context)->context.client().setScreen(new ResultScreen(result)));
        ClientPlayNetworking.registerGlobalReceiver(Protocol.LootState.ID,(state,context)->{
            if(context.client().currentScreen instanceof LootScreen screen)screen.update(state);
            else context.client().setScreen(new LootScreen(state));
        });
        ClientPlayNetworking.registerGlobalReceiver(Protocol.StashState.ID,(state,context)->{
            if(context.client().currentScreen instanceof StashScreen screen)screen.update(state);
            else if(state.revision()>=0)context.client().setScreen(new StashScreen(state));
            else if(context.client().player!=null)context.client().player.sendMessage(Text.literal(state.status()),false);
        });
        ClientPlayNetworking.registerGlobalReceiver(Protocol.LobbyState.ID,(state,context)->{
            try{
                com.google.gson.JsonObject data=com.google.gson.JsonParser.parseString(state.json()).getAsJsonObject();
                if(data.has("kind")&&data.get("kind").getAsString().equals("versus_buy")){
                    if(context.client().currentScreen instanceof VersusBuyScreen shop)shop.update(data);
                    else if(state.open())context.client().setScreen(new VersusBuyScreen(data));
                    return;
                }
                if(context.client().currentScreen instanceof LobbyPanel panel)panel.updateLobby(data);
                else if(state.open())context.client().setScreen(new OperationsScreen(data));
            }catch(RuntimeException e){dev.svextract.SvExtract.LOGGER.warn("Invalid lobby snapshot rejected",e);}
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->hud=null);
        ExtractionKeyBindings bindings=new ExtractionKeyBindings();
        Runnable openBriefing=()->ClientPlayNetworking.send(new Protocol.LobbyAction(0,"","",1,1,false));
        ClientTickEvents.END_CLIENT_TICK.register(client->bindings.tick(client,openBriefing));
        HudRenderCallback.EVENT.register((context,tickCounter)->render(context));
    }
    private void render(DrawContext context){
        if(!settings.visible||hud==null||MinecraftClient.getInstance().options.hudHidden)return;
        MinecraftClient client=MinecraftClient.getInstance();
        int x=settings.x,y=settings.y;
        context.getMatrices().push();context.getMatrices().scale((float)settings.scale,(float)settings.scale,1);
        context.fill(x,y,x+244,y+66,0xC0181C25);context.fill(x,y,x+3,y+66,0xFF52D3D1);
        context.drawText(client.textRenderer,Text.translatable("svextract.hud."+hud.phase().toLowerCase(java.util.Locale.ROOT)),x+9,y+7,0x52D3D1,false);
        boolean versusScoreMode=hud.phase().equals("VERSUS")&&(hud.objective().equals("versus_tdm")||hud.objective().equals("versus_domination"));
        context.drawText(client.textRenderer,versusScoreMode?Text.translatable("svextract.versus.result",hud.teamScore(),hud.enemyScore()):hud.phase().equals("VERSUS")?Text.translatable("svextract.versus.round_hud",hud.threat(),hud.teamScore(),hud.enemyScore()):hud.objective().equals("versus_score")?Text.translatable("svextract.versus.score",hud.teamScore(),hud.enemyScore()):Text.translatable("svextract.objective."+hud.objective()),x+9,y+22,0xFFFFFF,false);
        context.drawText(client.textRenderer,hud.phase().equals("VERSUS")?Text.translatable("svextract.versus.phase_hud",Text.translatable("svextract.objective."+hud.objective()),hud.remainingSeconds()/60,hud.remainingSeconds()%60):Text.translatable("svextract.hud.timer",hud.remainingSeconds()/60,hud.remainingSeconds()%60,hud.threat()),x+9,y+37,0xBDC8D7,false);
        if(hud.extractionSeconds()>0)context.drawText(client.textRenderer,Text.translatable(hud.phase().equals("VERSUS")?"svextract.versus.channel":hud.phase().equals("RESPAWNING")?"svextract.hud.respawn":"svextract.hud.extraction",hud.extractionSeconds()),x+9,y+51,0xFFE097,false);
        context.getMatrices().pop();
    }
    private static final class BriefingScreen extends Screen {
        BriefingScreen(){super(Text.translatable("svextract.briefing.title"));}
        @Override protected void init(){
            addDrawableChild(ButtonWidget.builder(Text.translatable("svextract.briefing.deploy"),b->{
                if(client!=null&&client.player!=null)client.player.networkHandler.sendChatCommand("svextract deploy");close();
            }).dimensions(width/2-100,height/2+70,200,20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"),b->close()).dimensions(width/2-100,height/2+95,200,20).build());
        }
        @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
            renderBackground(c,mouseX,mouseY,delta);super.render(c,mouseX,mouseY,delta);
            c.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-80,0x52D3D1);
            for(int i=0;i<6;i++)c.drawCenteredTextWithShadow(textRenderer,Text.translatable("svextract.briefing.line."+i),width/2,height/2-50+i*18,0xFFFFFF);
        }
        @Override public boolean shouldPause(){return false;}
    }
    private static final class ResultScreen extends Screen {
        private final Protocol.Result result;
        ResultScreen(Protocol.Result result){super(Text.translatable("svextract.result.title"));this.result=result;}
        @Override protected void init(){addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"),b->close()).dimensions(width/2-100,height/2+60,200,20).build());}
        @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
            renderBackground(c,mouseX,mouseY,delta);super.render(c,mouseX,mouseY,delta);
            c.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-60,0x52D3D1);
            c.drawCenteredTextWithShadow(textRenderer,Text.translatable("svextract.result."+result.outcome()),width/2,height/2-25,0xFFFFFF);
            c.drawCenteredTextWithShadow(textRenderer,result.mode().equals("versus")&&!result.outcome().equals("RECOVERED")?Text.translatable("svextract.versus.final_score",result.teamScore(),result.enemyScore()):Text.translatable("svextract.result.objectives",result.completedObjectives()),width/2,height/2,0xBDC8D7);
            String gear=result.equipmentRisk()?result.outcome().equals("EXTRACTED")?"svextract.result.gear_extracted":result.outcome().equals("RECOVERED")?"svextract.result.gear_recovered":"svextract.result.gear_lost":"svextract.result.training";
            c.drawCenteredTextWithShadow(textRenderer,Text.translatable(gear),width/2,height/2+24,0xBDC8D7);
        }
        @Override public boolean shouldPause(){return false;}
    }
}
