package io.github.aristheg201.cobblemonworld.qa;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.client.screen.*;
import io.github.aristheg201.cobblemonworld.network.QaControlPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.minecraft.core.*;
import net.minecraft.client.Screenshot;
import java.util.*;

/** Real entity/block interaction, real screen mouse handlers and Cobblemon's normal move-choice API. */
public final class NarrativeQaClient {
    private static String token="";private static int age,nodeAge,afterBattle;private static boolean interacted,hadBattle,captured,pendingFinish,phoneOpened,phonePinned;
    private static String expectedReply="";private static int historyFrames;private static final Set<String> REOPENED=new HashSet<>();
    private static String lastNode="";private static final Set<String> SHOTS=new HashSet<>();
    public static int perform(Minecraft mc,QaControlPayload packet){
        if(!token.equals(packet.secondary())){token=packet.secondary();age=0;nodeAge=0;interacted=false;phoneOpened=false;phonePinned=false;hadBattle=false;captured=false;pendingFinish=false;afterBattle=0;lastNode="";mc.setScreen(null);mc.getToasts().clear();}
        age++;if(pendingFinish){if(age<40)return 0;screenshot(mc,"world-"+token);return 1;}var control=new Gson().fromJson(packet.primary(),NarrativeQaServer.Control.class);
        if(!control.offer().isBlank() && control.stage().equals(control.offer()+".1") && !phonePinned){
            if(!phoneOpened && age>15){mc.player.getInventory().selected=0;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phoneOpened=true;age=0;return 0;}
            if(mc.screen instanceof TrainerPhoneScreen phone && age>20){phone.qaPinQuest(control.offer());screenshot(mc,"pin-"+token);phonePinned=true;age=0;return 0;}
            return 0;
        }
        if(phonePinned && !interacted && age<20)return 0;
        if(phonePinned && mc.screen instanceof TrainerPhoneScreen){mc.setScreen(null);screenshot(mc,"compass-"+token);}
        if(control.kind().equals("pc")){
            if(!interacted && age>20){var pos=new BlockPos(control.x(),control.y(),control.z());mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));interacted=true;age=0;return 0;}
            if(mc.screen instanceof com.cobblemon.mod.common.client.gui.pc.PCGUI pc && age>30){
                if(!captured){
                    var box=pc.getStorage().getBox();int slot=-1;
                    for(int i=0;i<30;i++){var mon=pc.getPc().get(new com.cobblemon.mod.common.api.storage.pc.PCPosition(box,i));if(mon!=null && mon.getSpecies().getName().equalsIgnoreCase(control.stage())){slot=i;break;}}
                    if(slot<0)throw new IllegalStateException("Claimed Legendary absent from native PC snapshot "+control.stage());
                    pc.mouseClicked(pc.getStorage().getX()+7+(slot%6)*27+12,pc.getStorage().getY()+11+(slot/6)*27+12,0);captured=true;age=0;return 0;
                }
                if(pc.getPreviewPokemon$common()==null || !pc.getPreviewPokemon$common().getSpecies().getName().equalsIgnoreCase(control.stage()))throw new IllegalStateException("Native PC selection failed "+control.stage());
                screenshot(mc,token);pc.closeNormally(true);return 1;
            }
            return 0;
        }
        if(control.kind().equals("capture")){
            var entity=mc.level.getEntity(control.entity());
            if(entity==null && age>100){screenshot(mc,"captured-"+token);return 1;}
            if(entity!=null && age%60==30){
                mc.setScreen(null);mc.player.getInventory().selected=8;
                double dx=entity.getX()-mc.player.getX(),dz=entity.getZ()-mc.player.getZ(),dy=entity.getY()+entity.getBbHeight()*.5-mc.player.getEyeY();
                mc.player.setYRot((float)(Math.toDegrees(Math.atan2(dz,dx))-90));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);screenshot(mc,"capture-"+token);
            }
            return 0;
        }
        var battle=com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getBattle();if(battle!=null){hadBattle=true;return 0;}
        if(hadBattle){if(++afterBattle<120)return 0;if(mc.screen instanceof DialogueScreen)screenshot(mc,"outcome-"+token);mc.setScreen(null);return 1;}
        if(!interacted && age>15){
            if(control.kind().equals("travel") || control.kind().equals("collect")){screenshot(mc,token);return 1;}
            if(control.entity()<0){var pos=new BlockPos(control.x(),control.y(),control.z());mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));}
            else {var entity=mc.level.getEntity(control.entity());if(entity==null)return 0;mc.gameMode.interact(mc.player,entity,InteractionHand.MAIN_HAND);}
            interacted=true;age=0;return 0;
        }
        if(mc.screen instanceof ShopScreen shop){
            if(!captured && age>20){screenshot(mc,token);captured=true;shop.qaClickBuy();}
            return captured && age>60?1:0;
        }
        if(control.kind().equals("heal") && interacted && mc.screen==null && age>100){screenshot(mc,token);return 1;}
        if(mc.screen instanceof DialogueScreen dialogue){
            var snapshot=dialogue.snapshot();String key=snapshot.text()+":"+snapshot.revision();
            if(!key.equals(lastNode)){lastNode=key;nodeAge=0;}nodeAge++;
            if(nodeAge<12)return 0;
            if(historyFrames>0){if(++historyFrames<12)return 0;screenshot(mc,"history-"+token);dialogue.qaToggleHistory();historyFrames=0;return 0;}
            if(control.stage().equals("archive_hale") && !snapshot.reply().isBlank() && !REOPENED.contains(token)){
                expectedReply=snapshot.reply();REOPENED.add(token);mc.setScreen(null);interacted=false;age=0;lastNode="";return 0;
            }
            if(!expectedReply.isBlank() && !snapshot.reply().isBlank()){
                if(!snapshot.reply().equals(expectedReply) || snapshot.history().stream().noneMatch(line->line.speaker().equals(mc.player.getGameProfile().getName()) && line.text().equals(expectedReply)))throw new IllegalStateException("Reopened conversation lost its player reply/history");
                screenshot(mc,"reopened-"+token);expectedReply="";dialogue.qaToggleHistory();historyFrames=1;return 0;
            }
            if(!captured){screenshot(mc,token);captured=true;}
            if(snapshot.text().endsWith(".line"))screenshot(mc,"choices-"+token);
            if(snapshot.text().endsWith(".line") && control.stage().equals("orin_summary") && nodeAge<24){dialogue.mouseScrolled(0,0,0,-1);if(nodeAge==23)screenshot(mc,"long-scroll-"+token);return 0;}
            if(!snapshot.reply().isBlank() && snapshot.revision()<30)screenshot(mc,"reply-"+token+"-"+snapshot.revision()+".png");
            String desired="stage:"+control.stage();int index=-1;
            for(int i=0;i<snapshot.choices().size();i++)if(snapshot.choices().get(i).id().equals(desired))index=i;
            if(index<0 && !control.offer().isBlank())for(int i=0;i<snapshot.choices().size();i++)if(snapshot.choices().get(i).id().equals("offer:"+control.offer()))index=i;
            if(index<0){
                if(snapshot.choices().stream().anyMatch(o->o.id().equals("answer0")))index=java.util.Set.of("shady_business.1","orin_summary").contains(control.stage())?0:Math.floorMod(control.stage().hashCode(),4);
                else index=0;
            }
            if(index>=snapshot.choices().size())index=0;
            String choice=snapshot.choices().get(index).id();dialogue.qaClick(index);nodeAge=0;
            if(choice.equals("finish") && !control.kind().equals("battle")){if(control.stage().equals("weather_duo_01.12") || control.stage().equals("weather_duo_01.16")){pendingFinish=true;age=0;return 0;}return 1;}
            return 0;
        }
        if(age>300 && interacted && !control.kind().equals("battle"))return 1;
        return 0;
    }
    private static void screenshot(Minecraft mc,String name){if(!SHOTS.add(name))return;mc.getToasts().clear();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->System.out.println("CWORLD_NARRATIVE_SCREENSHOT "+name));}
}
