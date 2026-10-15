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
import java.util.Arrays;

/** A native, server-authoritative whole-stack stash. No client inventory mutation or item NBT requests. */
public final class StashScreen extends Screen {
    private final ItemStack[] stored=new ItemStack[27];
    private long revision;
    private int page,pages,left,right,gridY,cell;
    private boolean pending;
    private String error="";
    public StashScreen(Protocol.StashState state){super(Text.translatable("svextract.stash.title"));Arrays.fill(stored,ItemStack.EMPTY);update(state);}
    public void update(Protocol.StashState state){
        pending=false;error=state.status();
        if(state.revision()<0)return;
        try{
            if(state.pages()<1||state.pages()>4||state.page()<0||state.page()>=state.pages())throw new IOException("Invalid storage page");
            var client=net.minecraft.client.MinecraftClient.getInstance();if(client.world==null)return;
            NbtCompound root=NbtIo.readCompound(new DataInputStream(new ByteArrayInputStream(state.items())),NbtSizeTracker.of(60*1024));
            ItemStack[] decoded=new ItemStack[27];Arrays.fill(decoded,ItemStack.EMPTY);
            NbtList items=root.getList("inventory",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<items.size();i++){
                NbtCompound item=items.getCompound(i);int slot=item.getByte("Slot")&255;
                if(slot>=27||!decoded[slot].isEmpty())throw new IOException("Invalid storage slot");
                decoded[slot]=ItemStack.fromNbt(client.world.getRegistryManager(),item).orElseThrow(()->new IOException("Unknown stored item"));
            }
            System.arraycopy(decoded,0,stored,0,27);revision=state.revision();page=state.page();pages=state.pages();
            if(this.client!=null)clearAndInit();
        }catch(Exception e){error="Storage display failed; items remain on the server";}
    }
    private void request(int action,int slot,int targetPage){
        if(pending)return;pending=true;error="";
        ClientPlayNetworking.send(new Protocol.StashAction(action,targetPage,slot,revision));
    }
    @Override protected void init(){
        cell=width<368?16:18;left=width/2-9*cell-14;right=width/2+12;gridY=55;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"),b->request(0,0,page-1)).dimensions(left,gridY+60,24,18).build()).active=page>0;
        addDrawableChild(ButtonWidget.builder(Text.literal(">"),b->request(0,0,page+1)).dimensions(left+9*cell-24,gridY+60,24,18).build()).active=page+1<pages;
        addDrawableChild(ButtonWidget.builder(Text.translatable("svextract.lobby.refresh"),b->request(0,0,page)).dimensions(width/2-106,height-28,102,20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"),b->close()).dimensions(width/2+4,height-28,102,20).build());
    }
    private ItemStack stack(boolean storage,int index){
        if(storage)return stored[index];
        return client==null||client.player==null?ItemStack.EMPTY:client.player.getInventory().getStack(index<27?index+9:index-27);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,0xFF0D1822);c.fill(0,0,width,30,0xFF142D38);c.fill(0,30,width,32,0xFF48D5C5);
        c.drawCenteredTextWithShadow(textRenderer,title,width/2,11,0xE4F8F6);
        c.drawTextWithShadow(textRenderer,Text.translatable("svextract.stash.storage"),left,41,0x48D5C5);
        c.drawTextWithShadow(textRenderer,Text.translatable("svextract.stash.loadout"),right,41,0x48D5C5);
        ItemStack hovered=ItemStack.EMPTY;
        for(int side=0;side<2;side++)for(int index=0;index<(side==0?27:36);index++){
            int x=(side==0?left:right)+(index%9)*cell,y=gridY+(index/9)*cell+(side==1&&index>=27?5:0);
            boolean over=mx>=x&&mx<x+cell&&my>=y&&my<y+cell;
            c.fill(x,y,x+cell-1,y+cell-1,over&&!pending?0xFF386875:0xFF203540);
            ItemStack item=stack(side==0,index);if(!item.isEmpty()){c.drawItem(item,x+1,y+1);c.drawItemInSlot(textRenderer,item,x+1,y+1);if(over)hovered=item;}
        }
        c.drawCenteredTextWithShadow(textRenderer,Text.literal((page+1)+" / "+pages),left+9*cell/2,gridY+65,0xBDC8D7);
        int y=gridY+93;
        for(var line:textRenderer.wrapLines(Text.translatable("svextract.stash.help"),Math.max(180,width-32))){c.drawCenteredTextWithShadow(textRenderer,line,width/2,y,0xBDC8D7);y+=10;}
        if(pending)c.drawCenteredTextWithShadow(textRenderer,Text.translatable("svextract.stash.saving"),width/2,y+4,0xFFD66F);
        if(!error.isEmpty()){
            y+=3;for(var line:textRenderer.wrapLines(Text.literal(error),width-24)){if(y+10>=height-30)break;c.drawCenteredTextWithShadow(textRenderer,line,width/2,y,0xFF9988);y+=10;}
        }
        super.render(c,mx,my,delta);if(!hovered.isEmpty())c.drawItemTooltip(textRenderer,hovered,mx,my);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&!pending){
            for(int side=0;side<2;side++)for(int index=0;index<(side==0?27:36);index++){
                int x=(side==0?left:right)+(index%9)*cell,y=gridY+(index/9)*cell+(side==1&&index>=27?5:0);
                if(mx>=x&&mx<x+cell&&my>=y&&my<y+cell&&!stack(side==0,index).isEmpty()){
                    request(side==0?2:1,side==0?index:index<27?index+9:index-27,page);return true;
                }
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public void close(){if(client!=null){client.setScreen(null);ClientPlayNetworking.send(new Protocol.LobbyAction(0,"","",1,1,false));}}
    @Override public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext c,int mx,int my,float delta){}
    @Override protected void applyBlur(float delta){}
}
