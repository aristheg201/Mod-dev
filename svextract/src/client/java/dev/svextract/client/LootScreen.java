package dev.svextract.client;

import dev.svextract.network.Protocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import java.io.*;

/** Crate contents come from the server. Taking never sends item contents or quantities. */
public final class LootScreen extends Screen {
    private Protocol.LootState state;
    private ItemStack preview=ItemStack.EMPTY;
    private boolean pending;
    public LootScreen(Protocol.LootState state){super(Text.translatable("svextract.loot.title"));update(state);}
    public void update(Protocol.LootState state){
        this.state=state;pending=false;
        try{
            var mc=net.minecraft.client.MinecraftClient.getInstance();
            NbtCompound root=NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(state.preview())),NbtSizeTracker.of(60*1024));
            preview=state.claimed()?ItemStack.EMPTY:ItemStack.fromNbt(mc.world.getRegistryManager(),root).orElse(ItemStack.EMPTY);
        }catch(Exception e){preview=ItemStack.EMPTY;}
        if(client!=null)clearAndInit();
    }
    @Override protected void init(){
        addDrawableChild(ButtonWidget.builder(Text.translatable(state.claimed()?"svextract.loot.empty":"svextract.loot.take"),b->{b.active=false;pending=true;ClientPlayNetworking.send(new Protocol.LootClaim(state.cache()));}).dimensions(width/2-95,height/2+35,190,23).build()).active=!state.claimed()&&!preview.isEmpty();
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"),b->close()).dimensions(width/2-95,height/2+65,190,20).build());
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xDD0D1822);c.fill(width/2-130,height/2-92,width/2+130,height/2+94,0xFF122733);
        c.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-79,0x48D5C5);
        c.drawCenteredTextWithShadow(textRenderer,Text.translatable("svextract.loot.cache."+state.cache()),width/2,height/2-59,0xBDC8D7);
        c.fill(width/2-12,height/2-30,width/2+12,height/2-6,0xFF294550);
        if(!preview.isEmpty()){c.drawItem(preview,width/2-8,height/2-26);c.drawItemInSlot(textRenderer,preview,width/2-8,height/2-26);c.drawCenteredTextWithShadow(textRenderer,preview.getName(),width/2,height/2+4,0xFFFFFF);}
        if(pending)c.drawCenteredTextWithShadow(textRenderer,Text.translatable("svextract.loot.pending"),width/2,height/2+20,0xFFD66F);
        if(!state.status().isEmpty())c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(state.status(),250),width/2,height/2+20,0xFF9988);
        super.render(c,mx,my,delta);
        if(mx>=width/2-12&&mx<width/2+12&&my>=height/2-30&&my<height/2-6&&!preview.isEmpty())c.drawItemTooltip(textRenderer,preview,mx,my);
    }
    @Override public void tick(){if(client!=null&&client.player!=null&&(client.player.isSpectator()||SvExtractClient.currentHud()==null))close();}
    @Override public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    @Override protected void applyBlur(float delta){}
}
