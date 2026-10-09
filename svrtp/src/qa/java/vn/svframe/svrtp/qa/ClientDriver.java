package vn.svframe.svrtp.qa;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.ClickType;
import java.util.*;

/** Separate test-only client JAR. Production SVRTP has no client entrypoint or custom screen. */
public final class ClientDriver implements ClientModInitializer {
    int ticks,stage=-1,age,screenStep;boolean connecting;
    String command="",currency="";final Set<String> shots=new HashSet<>();
    @Override public void onInitializeClient() {
        if(!Boolean.getBoolean("svrtp.qa.client"))return;
        ClientReceiveMessageEvents.GAME.register((message,overlay)->{
            var text=message.getString();if(text.startsWith("SVRTP_QA_STEP ")) {
                String[] fields=text.split(" ");stage=Integer.parseInt(fields[1]);command=fields[2];currency=fields[3];age=0;screenStep=0;
            }
            if(text.equals("SVRTP_QA_FINISHED"))Minecraft.getInstance().stop();
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    void tick(Minecraft mc) {
        ticks++;
        if(mc.player==null) {
            if(!atlasReady(mc) || mc.getOverlay()!=null) {ticks=0;return;}
            if(ticks>100 && !connecting && mc.screen!=null) {connecting=true;String address="127.0.0.1:25572";
                ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Internal SVRTP QA",address,ServerData.Type.OTHER),false,null);}
            return;
        }
        mc.getToasts().clear();
        if(stage<0)return;
        age++;
        if(age==10) {mc.setScreen(null);if(command.equals("spam")) {mc.options.guiScale().set(3);mc.resizeDisplay();}
            mc.player.connection.sendCommand(command.equals("menu") || command.equals("spam")?"rtp":"rtp "+command);}
        if(mc.screen instanceof ContainerScreen screen && age>25) {
            if(screenStep==0 && (command.equals("menu") || command.equals("spam"))) {
                screenshot(mc,"rtp-menu-scale-"+mc.options.guiScale().get()+".png");click(mc,screen,20);screenStep=1;age=12;
            }else if(screenStep<=1) {
                screenshot(mc,"payment-"+stage+".png");
                click(mc,screen,currency.equals("bc")?20:24);
                if(command.equals("spam")) {click(mc,screen,20);click(mc,screen,20);mc.player.connection.sendCommand("rtp resource");mc.player.connection.sendCommand("rtp resource");}
                screenStep=2;
            }
        }
        if(age==100) {screenshot(mc,"arrival-"+stage+".png");mc.player.connection.sendCommand("svrtpqatest ack "+stage);}
        if(age>400)throw new IllegalStateException("SVRTP client QA timed out at step "+stage);
    }
    boolean atlasReady(Minecraft mc) {
        try {mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(net.minecraft.resources.ResourceLocation.parse("minecraft:block/stone"));return true;}
        catch(IllegalStateException notReady) {return false;}
    }
    void click(Minecraft mc,ContainerScreen screen,int slot) {
        // Actual screen mouse input chooses a native chest slot.
        int left=(mc.getWindow().getGuiScaledWidth()-176)/2;
        int top=(mc.getWindow().getGuiScaledHeight()-222)/2;
        screen.mouseClicked(left+8+(slot%9)*18+8,top+18+(slot/9)*18+8,0);
    }
    void screenshot(Minecraft mc,String name) {mc.getToasts().clear();if(shots.add(name))Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->System.out.println("SVRTP_QA_SCREENSHOT "+name));}
}
