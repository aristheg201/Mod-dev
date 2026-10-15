package dev.svextract.qa;

import dev.svextract.client.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

/** Uses native widgets and item-grid clicks; never sends inventory contents or writes player saves. */
final class NativeStashQa {
    private int ticks,stage,baseline;
    private final String action=System.getProperty("svextract.qa.stash","");
    private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger("svextract-stash-qa");
    private int guns(MinecraftClient c){int count=0;for(int i=0;i<36;i++){var stack=c.player.getInventory().getStack(i);if(Registries.ITEM.getId(stack.getItem()).toString().equals("pointblank:glock17"))count+=stack.getCount();}return count;}
    void tick(MinecraftClient c){
        if(c.player==null||c.world==null)return;ticks++;
        if(ticks%100==0)log.info("Native stash QA {} stage {} gun count {} dimension {}",action,stage,guns(c),c.world.getRegistryKey().getValue());
        if(stage==0&&c.currentScreen==null&&ticks%40==0){c.player.networkHandler.sendChatCommand("svextract lobby");return;}
        if(stage==0&&c.currentScreen instanceof OperationsScreen lobby&&ticks>80){
            if(!c.world.getRegistryKey().getValue().toString().equals("svextract:haven9")){
                if(ticks%40==0)c.player.networkHandler.sendChatCommand("svextract haven");return;
            }
            for(var child:c.currentScreen.children())if(child instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.getMessage().getString().equals(Text.translatable("svextract.stash.title").getString())){
                if(button.active){c.currentScreen.mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0);stage=1;ticks=0;}return;
            }
        }
        if(stage==1&&c.currentScreen instanceof StashScreen&&ticks>30){
            baseline=guns(c);int x=c.getWindow().getScaledWidth()/2,y=55;
            if(action.equals("deposit")){
                int slot=-1;for(int i=0;i<36;i++)if(Registries.ITEM.getId(c.player.getInventory().getStack(i).getItem()).toString().equals("pointblank:glock17")){slot=i;break;}
                if(slot<0){if(ticks>400){log.error("Native stash QA missing fixture firearm");stage=99;}return;}
                int index=slot<9?slot+27:slot-9;x+=12+(index%9)*18+8;y+=(index/9)*18+(index>=27?5:0)+8;
            }else{x-=176;x+=8;y+=8;}
            c.currentScreen.mouseClicked(x,y,0);stage=2;ticks=0;
        }
        if(stage==2&&c.currentScreen instanceof StashScreen&&ticks>40){
            int expected=baseline+(action.equals("deposit")?-1:1);
            if(guns(c)!=expected){if(ticks>400){log.error("Native stash QA failed: expected {} gun(s), actual {}",expected,guns(c));stage=99;}return;}
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,"stash-"+action+"-native.png",c.getFramebuffer(),message->log.info("Native stash screenshot {}",message.getString()));
            log.info("NATIVE_STASH_VERIFIED action={} before={} after={}",action,baseline,guns(c));stage=3;
        }
    }
}
