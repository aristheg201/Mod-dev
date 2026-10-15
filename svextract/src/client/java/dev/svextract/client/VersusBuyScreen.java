package dev.svextract.client;

import com.google.gson.*;
import dev.svextract.network.Protocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Purchases request a catalog key; the server owns the wallet, price and actual firearm. */
public final class VersusBuyScreen extends Screen {
    private JsonObject state;
    public VersusBuyScreen(JsonObject state){super(Text.translatable("svextract.versus.buy_title"));this.state=state;}
    public void update(JsonObject state){this.state=state;clearAndInit();}
    @Override protected void init(){
        int index=0;
        for(var entry:state.getAsJsonArray("products")){
            JsonObject product=entry.getAsJsonObject();String id=product.get("id").getAsString();int price=product.get("price").getAsInt();
            var button=addDrawableChild(ButtonWidget.builder(Text.translatable("svextract.versus.product",Text.translatable("svextract.versus.product."+id),price),b->{b.active=false;ClientPlayNetworking.send(new Protocol.LobbyAction(10,id,"",1,0,false));}).dimensions(width/2-114+(index%2)*118,height/2-42+(index/2)*25,114,21).build());
            button.active=state.get("alive").getAsBoolean()&&state.get("phase").getAsString().equals("BUY")&&state.get("cash").getAsInt()>=price;index++;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"),b->close()).dimensions(width/2-110,height-28,220,20).build());
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xFF0D1822);c.fill(0,0,width,32,0xFF17343F);
        c.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0x48D5C5);
        c.drawCenteredTextWithShadow(textRenderer,Text.translatable("svextract.versus.wallet",state.get("round").getAsInt(),state.get("cash").getAsInt()),width/2,42,0xFFFFFF);
        c.drawCenteredTextWithShadow(textRenderer,Text.translatable("svextract.versus.match_cash"),width/2,height-47,0xBDC8D7);
        super.render(c,mx,my,delta);
    }
    @Override public void tick(){var hud=SvExtractClient.currentHud();if(hud!=null&&!hud.objective().equals("versus_buy"))close();}
    @Override public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    @Override protected void applyBlur(float delta){}
}
