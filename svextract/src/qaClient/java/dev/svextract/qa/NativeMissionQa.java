package dev.svextract.qa;

import dev.svextract.client.SvExtractClient;
import dev.svextract.network.Protocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.slf4j.LoggerFactory;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Optional test mod, excluded from every deliverable JAR. Controls a real Fabric client.
 * Walks with ordinary key input and sends ordinary entity/block interaction packets.
 * Never teleports through the mission, completes server objectives, or mutates server inventory.
 */
public final class NativeMissionQa implements ClientModInitializer {
    private final NativeStashQa stashQa=new NativeStashQa();
    private int stage,ticks,waypoint;
    private String lastObjective="";
    private boolean companionAssigned;
    private boolean lobbyScreenshot;
    private boolean raidScreenshot,resultScreenshot;
    private int resultVisibleTicks,raidVisibleTicks;
    private int handshakeWait=-1;
    private int initialAmmunition;
    private int lobbyVisibleTicks,lootVisibleTicks;
    private boolean lootScreenshot,buyScreenshot;
    private int buyVisibleTicks;
    private final org.slf4j.Logger log=LoggerFactory.getLogger("svextract-qa");
    @Override public void onInitializeClient(){
        if(Boolean.getBoolean("svextract.qa.delayHandshake")){
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.unregisterGlobalReceiver(Protocol.Hello.ID.id());
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->handshakeWait=-1);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(Protocol.Hello.ID,(payload,context)->{handshakeWait=40;log.info("QA delaying client handshake by 40 ticks to exercise recovery-first login ordering");});
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient c){
        if(handshakeWait>=0){if(handshakeWait--==0&&c.getNetworkHandler()!=null){net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(Protocol.hello());log.info("QA delayed client handshake sent after durable recovery window");}return;}
        if(!System.getProperty("svextract.qa.stash", "").isEmpty()){stashQa.tick(c);return;}
        if(Boolean.getBoolean("svextract.qa.manual"))return;
        if(c.player==null||c.world==null||c.interactionManager==null)return;
        ticks++;
        if(stage==8){stop(c);return;}
        if(ticks%100==0)log.info("QA stage {} position {} world {} hud {}",stage,c.player.getPos(),c.world.getRegistryKey().getValue(),SvExtractClient.currentHud());
        if(c.currentScreen!=null){
            if(c.currentScreen instanceof dev.svextract.client.OperationsScreen.WorldScreen){
                if(ticks%20==0)click(c,Text.translatable("svextract.worlds.haven").getString());return;
            }
            if(c.currentScreen instanceof dev.svextract.client.StashScreen){
                if(ticks%40==0)click(c,Text.translatable("gui.done").getString());return;
            }
            if(c.currentScreen instanceof dev.svextract.client.VersusBuyScreen){
                buyVisibleTicks++;
                if(!buyScreenshot&&buyVisibleTicks>20&&!c.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isEmpty()){
                    buyScreenshot=true;net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,"versus-buy-native.png",c.getFramebuffer(),text->log.info("QA real match purchase screenshot {}",text.getString()));
                }
                if(ticks%20==0)click(c,Text.translatable("svextract.versus.product",Text.translatable("svextract.versus.product.armor"),650).getString());return;
            }
            if(c.currentScreen instanceof dev.svextract.client.LootScreen){
                lootVisibleTicks++;
                if(!lootScreenshot&&lootVisibleTicks>=10){lootScreenshot=true;net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,"loot-crate-native.png",c.getFramebuffer(),text->log.info("QA loot GUI screenshot {}",text.getString()));}
                if(lootVisibleTicks>=20&&ticks%20==0)click(c,Text.translatable("svextract.loot.take").getString());
                if(lootVisibleTicks>=40&&ticks%20==10)click(c,Text.translatable("gui.done").getString());return;
            }
            if(c.currentScreen instanceof dev.svextract.client.OperationsScreen lobby){
                stop(c);
                lobbyVisibleTicks++;
                if(!lobbyScreenshot&&lobbyVisibleTicks>=20){
                    lobbyScreenshot=true;net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,"operations-lobby-native.png",c.getFramebuffer(),text->log.info("QA native screenshot: {}",text.getString()));
                }
                String desired=System.getProperty("svextract.qa.mode","training");
                String mode=Text.translatable("svextract.mode."+desired).getString();
                if(!lobby.selectedMode().equals(desired))click(c,mode);
                if(desired.equals("operations")&&ticks%60==15)click(c,Text.translatable("svextract.lobby.accept_risk").getString());
                if(ticks%60==10&&(!c.world.getRegistryKey().getValue().toString().startsWith("svextract:")||c.player.getY()<64))click(c,Text.translatable("svextract.worlds.title").getString());
                if(ticks%60==20&&lobbyVisibleTicks>=25){initialAmmunition=ammunition(c);click(c,Text.translatable("svextract.lobby.deploy").getString());}
                if(ticks%60==40)click(c,Text.translatable("svextract.lobby.refresh").getString());
                return;
            }
            if(java.util.Set.of("svextract:haven9","svextract:skyrim").contains(c.world.getRegistryKey().getValue().toString())&&lobbyScreenshot&&SvExtractClient.currentHud()==null){
                if(raidVisibleTicks==0){click(c,Text.translatable("gui.done").getString());if(c.currentScreen==null)c.player.networkHandler.sendChatCommand("svextract lobby");return;}
                stop(c);resultVisibleTicks++;
                if(!resultScreenshot&&resultVisibleTicks>=20){resultScreenshot=true;stage=8;net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,"post-match-native.png",c.getFramebuffer(),text->log.info("QA result screenshot: {}",text.getString()));}
                return;
            }
            if(stage==0 || stage==8)c.setScreen(null);else return;
        }
        String world=c.world.getRegistryKey().getValue().toString();
        if(world.equals("svextract:raid")){
            raidVisibleTicks++;
            if(!raidScreenshot&&raidVisibleTicks>=60){raidScreenshot=true;net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,"raid-native.png",c.getFramebuffer(),text->log.info("QA raid screenshot: {}",text.getString()));}
        }
        if(System.getProperty("svextract.qa.mode","training").equals("versus")&&world.equals("svextract:raid")){
            Protocol.Hud versus=SvExtractClient.currentHud();
            if(versus!=null&&versus.objective().equals("versus_attack")){
                KeyBinding.onKeyPressed(InputUtil.fromKeyCode(57,0));
                navigateAndInteract(c,new double[][]{{-27,20},{-10,20}},new BlockPos(-10,65,20));
            }else{
                stop(c);
                if(Boolean.getBoolean("svextract.qa.gunfire")&&versus!=null&&(versus.objective().equals("versus_bomb")||versus.objective().equals("versus_defend"))){
                    KeyBinding.onKeyPressed(InputUtil.fromKeyCode(49,0));c.options.attackKey.setPressed(ticks%100<40);
                    if(ticks%100==50)KeyBinding.onKeyPressed(InputUtil.fromKeyCode(82,0));
                }
            }
            if(ticks%100==0)log.info("QA DEFUSAL authoritative state {}",versus);return;
        }
        if(Boolean.getBoolean("svextract.qa.gunfire")&&world.equals("svextract:raid")){
            // Real client input -> Point Blank controller -> normal server firearm packet.
            // No synthetic damage, firearm adapter invocation, or server objective mutation.
            stop(c);c.options.attackKey.setPressed(ticks%100<40);
            if(ticks%100==50)KeyBinding.onKeyPressed(InputUtil.fromKeyCode(82,0));
            return;
        }
        if(stage==0){
            if(world.equals("svextract:haven9")){stage=1;return;}
            if(ticks%100==1)c.player.networkHandler.sendChatCommand("svextract haven");return;
        }
        if(stage==1){
            if(world.equals("svextract:raid")){stage=2;waypoint=0;return;}
            if(!move(c,0.5,-8.5))return;
            if(ticks%40!=0)return;
            c.world.getEntitiesByClass(VillagerEntity.class,c.player.getBoundingBox().expand(5),v->true).stream().findFirst().ifPresent(npc->{
                stop(c);c.interactionManager.interactEntity(c.player,npc,Hand.MAIN_HAND);log.info("QA interacted with mission-control NPC");
            });return;
        }
        if(stage>=2&&stage<=5){
            Protocol.Hud hud=SvExtractClient.currentHud();if(hud==null)return;
            if(Boolean.getBoolean("svextract.qa.companion")&&!companionAssigned){
                companionAssigned=true;
                c.player.networkHandler.sendChatCommand("svextract companiongun");
                KeyBinding.onKeyPressed(InputUtil.fromKeyCode(72,0));
                log.info("QA requested companion gun assignment and normal Cobblemon send-out key");
            }
            if(!hud.objective().equals(lastObjective)){lastObjective=hud.objective();waypoint=0;log.info("QA authoritative objective now {}",lastObjective);}
            if(Boolean.getBoolean("svextract.qa.scavenge")){
                if(stage==2){
                    if(ammunition(c)>initialAmmunition){stage=3;waypoint=0;log.info("QA verified new physical loot ammunition: {} -> {}",initialAmmunition,ammunition(c));}
                    else navigateAndInteract(c,new double[][]{{-4,-1.5}},new BlockPos(-4,65,-4));
                }else if(hud.extractionSeconds()>0){stage=6;stop(c);log.info("QA free extraction after physical loot, optional research incomplete");}
                else navigateAndInteract(c,new double[][]{{-17,-8},{-17,-12}},new BlockPos(-19,65,-12));
                return;
            }
            switch(hud.objective().replace("operation_","")){
                case "power" -> navigateAndInteract(c,new double[][]{{-13,0},{-13,5},{-12,6}},new BlockPos(-12,65,8));
                case "research_train" -> navigateAndInteract(c,new double[][]{{-13,0},{-13,-1.5},{12.5,-1.5},{12.5,13},{12.5,16}},new BlockPos(16,65,16));
                case "data_core" -> navigateAndInteract(c,new double[][]{{22.5,16}},new BlockPos(24,65,16));
                case "evacuation" -> {
                    if(hud.extractionSeconds()>0){stage=6;stop(c);log.info("QA evacuation countdown started");}
                    else navigateAndInteract(c,new double[][]{{24.5,16},{24.5,12},{4,0},{-16,-8},{-17,-12}},new BlockPos(-19,65,-12));
                }
            }
            return;
        }
        if(stage==6){
            stop(c);
            if(world.equals("svextract:haven9")){stage=8;log.info("QA EXTRACTION RETURNED TO HAVEN-9");c.player.networkHandler.sendChatCommand("svextract progress");}
            return;
        }
        if(stage==8){stop(c);}
    }
    private int ammunition(MinecraftClient c){return c.player.getInventory().main.stream().filter(item->net.minecraft.registry.Registries.ITEM.getId(item.getItem()).toString().equals("pointblank:ammo9mm")).mapToInt(net.minecraft.item.ItemStack::getCount).sum();}
    private void click(MinecraftClient client,String label){
        for(var element:client.currentScreen.children())if(element instanceof net.minecraft.client.gui.widget.ButtonWidget button&&button.active&&button.getMessage().getString().equals(label)){
            client.currentScreen.mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0);log.info("QA native GUI click {}",label);return;
        }
    }
    private void navigateAndInteract(MinecraftClient c,double[][] route,BlockPos target){
        Protocol.Hud hud=SvExtractClient.currentHud();if(hud!=null)target=target.add(hud.originX(),0,0);
        if(waypoint<route.length){if(move(c,route[waypoint][0],route[waypoint][1]))waypoint++;return;}
        stop(c);Vec3d direction=Vec3d.ofCenter(target).subtract(c.player.getEyePos());
        c.player.setYaw((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
        c.player.setPitch((float)-Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z))));
        if(ticks%20==0){c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(target),Direction.UP,target,false));log.info("QA ordinary block interaction at {}",target);}
    }
    private boolean move(MinecraftClient c,double x,double z){
        Protocol.Hud hud=SvExtractClient.currentHud();if(c.world!=null&&c.world.getRegistryKey().getValue().toString().equals("svextract:raid")&&hud!=null)x+=hud.originX();
        double dx=x-c.player.getX(),dz=z-c.player.getZ();if(dx*dx+dz*dz<0.4){stop(c);return true;}
        c.player.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));c.player.setPitch(0);
        c.options.forwardKey.setPressed(true);c.options.jumpKey.setPressed(c.player.horizontalCollision&&c.player.isOnGround());return false;
    }
    private void stop(MinecraftClient c){c.options.forwardKey.setPressed(false);c.options.jumpKey.setPressed(false);c.options.attackKey.setPressed(false);}
}
