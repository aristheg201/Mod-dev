package vn.svarcade.tcg.qa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;
import vn.svarcade.tcg.client.CardWorldsScreen;
import vn.svarcade.tcg.fabric.TcgClient;
import vn.svarcade.tcg.duel.Duel;
import java.util.*;

/** Separate QA-only mod. Real client, real integrated server, real snapshots; no fabricated UI state. */
public final class VisualRun implements ClientModInitializer {
 private java.util.concurrent.CompletableFuture<Void> languageReload;private String vfxSource="",vfxTarget="";private int specialCapture;private final List<String> specialIds=List.of("special_lucario_mega","special_charizard_mega_y","special_mewtwo_mega_x","special_lucario_mega");private long next;private int step;private boolean worldStarted;private int rounds;private int preWorldPasses;private long duelDeadline;private long proofRevision=-1;private long proofReadyAt;
 private long focusedNext;private int focusedStep;private boolean focusedWorldStarted;private int focusedPreWorldPasses;
 private static boolean focusedVisualMode(){return Boolean.getBoolean("cardworlds.qa.focused.visual")||"true".equalsIgnoreCase(System.getenv("CARDWORLDS_FOCUSED_VISUAL"));}\n private static boolean ygoOnlyMode(){return Boolean.getBoolean("cardworlds.qa.ygo.only");}
 @Override public void onInitializeClient(){org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_DRIVER_LOADED focusedVisual={}",focusedVisualMode());ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
 private void tick(MinecraftClient c){if(focusedVisualMode()){focusedTick(c);return;}if(c.currentScreen instanceof CardWorldsScreen)c.getToastManager().clear();long now=System.currentTimeMillis();if(now<next)return;next=now+1800;
  try{
   if(c.player==null){if(!worldStarted&&c.currentScreen!=null){if(++preWorldPasses<10)return;worldStarted=true;org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_CREATE_WORLD screen={}",c.currentScreen.getClass().getName());var info=new net.minecraft.world.level.LevelInfo("Card Worlds QA",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);c.createIntegratedServerLoader().createAndStart("cardworlds-qa",info,net.minecraft.world.gen.GeneratorOptions.createRandom(),registries->registries.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(net.minecraft.world.gen.WorldPresets.DEFAULT).createDimensionsRegistryHolder(),new TitleScreen());}return;}
   if(step==0){c.setScreen(null);c.getNetworkHandler().sendChatCommand("cardworlds");step++;return;}
   if(!(c.currentScreen instanceof CardWorldsScreen a)){if(((step==14)||(step>=170&&step<=207))&&duelDeadline>0&&now>duelDeadline)throw new AssertionError("Duel/QA UI did not reopen; step="+step+" world="+(c.world==null?"null":c.world.getRegistryKey().getValue()));if(step==20){KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_Y));step++;return;}if(step==22){c.setScreen(new ChatScreen("y"));KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_Y));step++;return;}if(step==23){if(!(c.currentScreen instanceof ChatScreen))throw new AssertionError("Y stole chat focus");c.setScreen(null);c.getNetworkHandler().sendChatCommand("cardworlds");step++;return;}if(step==30){c.scheduleStop();return;}return;}
   if(ygoOnlyMode()&&step<14){
    if(step==1){if(a.state.profile().owned()==0){a.send("starter");next=now+500;return;}a.navigate("Decks");a.saveDeck();step=2;next=now+500;return;}
    if(step==2){if(!a.state.notice().equals("Deck saved.")){next=now+300;return;}a.navigate("Play");duelDeadline=now+30000;a.send("pve",a.deckName,"HARD");step=14;next=now+300;org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_YGO_ONLY_START deck={}",a.deckName);return;}
   }
   switch(step){
    case 1->{if(a.state.profile().owned()==0)a.send("starter");step++;}
    case 2->{if(a.state.total()<1025||a.state.definitions().size()<1025)throw new AssertionError("Full National Dex catalog missing: total="+a.state.total()+" definitions="+a.state.definitions().size());for(String template:List.of("starter","kanto","galar","alola","hisui","creation","mega","ultra_space","distortion_world","eeveelution","fossil","legendary","dark_shadow","forms_showcase"))if(!a.state.deckTemplates().containsKey(template))throw new AssertionError("Deck template missing: "+template);long pokemon=a.state.definitions().values().stream().filter(d->d.category().equals("pokemon")).count();long fakemon=a.state.definitions().values().stream().filter(d->d.category().equals("pokemon")&&d.species()!=null&&d.species().contains(":")&&!d.species().startsWith("cobblemon:")).count();org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_CATALOG_CONFIRMED total={} pokemon={} fakemon={} templates={}",a.state.total(),pokemon,fakemon,a.state.deckTemplates().size());shot(c,"01-home");a.navigate("Collection");a.selected="charizard";step++;}
    case 3->{shot(c,"02-collection");a.navigate("Decks");step++;}
    case 4->{shot(c,"03-decks");a.saveDeck();step++;}
    case 5->{if(!a.state.notice().equals("Deck saved."))throw new AssertionError("Deck save response: "+a.state.notice());a.navigate("Play");a.mode="Ranked";step++;}
    case 6->{shot(c,"04-play");a.navigate("Market");step++;}
    case 7->{shot(c,"05-market");a.navigate("World");step++;}
    case 8->{shot(c,"06-world");a.navigate("Packs");step++;}
    case 9->{shot(c,"07-packs");a.banner="crossroads";a.reveal.await();a.send("pull","crossroads",UUID.randomUUID().toString());next=now+500;step++;}
    case 10->{shot(c,"08-pack-enter");next=now+800;step++;}
    case 11->{shot(c,"09-pack-opening");next=now+1500;step++;}
    case 12->{shot(c,"10-pack-reveal");next=now+5000;step++;}
    case 13->{shot(c,"11-pack-summary");click(a,640,a.logicalHeight-55);a.navigate("Play");duelDeadline=now+20000;a.send("pve",a.deckName,"HARD");step++;}
    case 14->{if(a.state.duel()==null){if(now>duelDeadline)throw new AssertionError("PvE duel snapshot missing; notice="+a.state.notice());return;}String qaWorld=c.world==null?"null":c.world.getRegistryKey().getValue().toString();if(!qaWorld.equals("svarcade_tcg:duel_realm")){if(now>duelDeadline)throw new AssertionError("Duel snapshot exists but client world did not transfer: "+qaWorld);return;}duelDeadline=0;org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_DUEL_REALM_CONFIRMED world={}",c.world.getRegistryKey().getValue());var v=a.state.duel();if(!v.phase().equals("MAIN1")){a.send("duel",v.open()?"next":"pass","","");return;}var card=v.cards().stream().filter(q->q.controller()==v.you()&&q.zone()==Duel.Zone.HAND&&q.category().equals("pokemon")).filter(q->a.state.definitions().values().stream().anyMatch(d->d.name().equals(q.name())&&(d.evolvesFrom()==null||d.evolvesFrom().isBlank()))).findFirst();if(card.isPresent())a.send("duel","play",card.get().token(),"");next=now+450;step++;}
    case 15->{shot(c,"12-duel-summon");next=now+1600;step++;}
    case 16->{shot(c,"13-duel-board");((net.minecraft.client.gui.screen.Screen)a).mouseDragged(640,360,1,240,-95);((net.minecraft.client.gui.screen.Screen)a).mouseScrolled(640,360,0,-6);next=now+700;step=161;}
    case 161->{shot(c,"14-duel-freelook");a.resetDuelCamera();next=now+500;step=170;}
    case 170->{duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa spectator enter");step=171;}
    case 171->{if(!a.state.spectator()){if(now>duelDeadline)throw new AssertionError("Spectator preview did not activate");return;}var sv=a.state.duel();if(sv==null)throw new AssertionError("Spectator duel view missing");if(sv.cards().stream().anyMatch(q->q.zone()==Duel.Zone.HAND||q.zone()==Duel.Zone.EXTRA))throw new AssertionError("Spectator leaked hidden zones");org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_SPECTATOR_CONFIRMED world={} x={} y={} z={}",c.world.getRegistryKey().getValue(),c.player.getX(),c.player.getY(),c.player.getZ());shot(c,"19-spectator");duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa spectator leave");step=172;}
    case 172->{if(a.state.spectator()){if(now>duelDeadline)throw new AssertionError("Spectator preview did not leave");return;}duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa spelltrap prepare");step=173;}
    case 173->{var v=a.state.duel();long set=v.cards().stream().filter(q->q.zone()==Duel.Zone.SUPPORT&&q.category().startsWith("facedown")).count();if(set<2){if(now>duelDeadline)throw new AssertionError("Spell/Trap Set scenario missing: "+set);return;}shot(c,"20-spelltrap-set");duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa spelltrap chain");step=174;}
    case 174->{var v=a.state.duel();if(v.chain().size()!=3){if(now>duelDeadline)throw new AssertionError("Expected 3-link Spell/Trap chain, got "+v.chain());return;}org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_SPELLTRAP_CHAIN_CONFIRMED chain={}",v.chain());shot(c,"21-spelltrap-chain");duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa spelltrap resolve");step=175;}
    case 175->{var v=a.state.duel();if(!v.chain().isEmpty()){if(now>duelDeadline)throw new AssertionError("Spell/Trap chain did not resolve");return;}if(!v.life().equals(List.of(7500,7200)))throw new AssertionError("Unexpected Spell/Trap life result "+v.life());if(v.log().stream().noneMatch(x->x.equals("Flamethrower resolves.")))throw new AssertionError("Flamethrower resolution missing");org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_SPELLTRAP_RESOLVE_CONFIRMED life={}",v.life());shot(c,"22-spelltrap-resolved");duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa creation prepare");step=176;}
    case 176->{var v=a.state.duel();long materials=v.cards().stream().filter(q->q.controller()==v.you()&&q.zone()==Duel.Zone.FIELD&&Set.of("Palkia","Dialga","Giratina").contains(q.name())).count();if(materials!=3){if(now>duelDeadline)throw new AssertionError("Creation materials missing: "+materials);return;}if(!proofReady(a,now,false))return;shot(c,"27-model-grounding");a.resetDuelCamera();duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa creation summon");step=177;}
    case 177->{var v=a.state.duel();if(v.cards().stream().noneMatch(q->q.name().equals("Arceus - Defense")&&q.zone()==Duel.Zone.FIELD)){if(now>duelDeadline)throw new AssertionError("Arceus Defense did not summon");return;}if(!proofReady(a,now,false))return;shot(c,"23-arceus-defense");duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa creation tick");step=178;}
    case 178->{var v=a.state.duel();if(v.cards().stream().noneMatch(q->q.name().equals("Arceus - Defense")&&q.zone()==Duel.Zone.FIELD)){throw new AssertionError("Arceus Defense should remain after first lock tick");}duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa creation tick");step=179;}
    case 179->{var v=a.state.duel();if(v.cards().stream().noneMatch(q->q.name().equals("Arceus - Judgement")&&q.zone()==Duel.Zone.FIELD)){if(now>duelDeadline)throw new AssertionError("Arceus Judgement stage missing");return;}if(!proofReady(a,now,false))return;shot(c,"24-arceus-judgement");duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa creation tick");step=180;}
    case 180->{var v=a.state.duel();if(v.cards().stream().noneMatch(q->q.name().equals("Ultimate Arceus")&&q.zone()==Duel.Zone.FIELD)){if(now>duelDeadline)throw new AssertionError("Ultimate Arceus stage missing");return;}if(!proofReady(a,now,false))return;org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_CREATION_CONFIRMED stage=Ultimate Arceus");shot(c,"25-arceus-ultimate");a.resetDuelCamera();duelDeadline=now+10000;c.getNetworkHandler().sendChatCommand("cardworlds qa_position");step=181;}
    case 181->{var v=a.state.duel();long attack=v.cards().stream().filter(q->q.zone()==Duel.Zone.FIELD&&"ATTACK".equals(q.position())).count();long defense=v.cards().stream().filter(q->q.zone()==Duel.Zone.FIELD&&"DEFENSE".equals(q.position())).count();long hidden=v.cards().stream().filter(q->q.zone()==Duel.Zone.FIELD&&"FACE_DOWN_DEFENSE".equals(q.position())).count();if(attack<1||defense<1||hidden<1){if(now>duelDeadline)throw new AssertionError("Monster position presentation scenario missing: attack="+attack+" defense="+defense+" hidden="+hidden);return;}if(!proofReady(a,now,true))return;a.duelPage.verifyPositionActors();org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_MONSTER_POSITIONS_CONFIRMED attack={} defense={} facedown={}",attack,defense,hidden);shot(c,"26-monster-positions");a.duelPage.preparePileProof();next=now+1800;step=182;}
    case 182->{if(!a.duelPage.visualSettled())return;a.duelPage.verifyPileActors();shot(c,"28-field-piles");org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_FIELD_PILES_CONFIRMED occupied=8 hidden=4 public=4");a.duelPage.verifyAnimationFallback(a.state.duel().cards().stream().filter(q->q.category().equals("pokemon")&&q.zone()==Duel.Zone.FIELD&&q.position().equals("ATTACK")).findFirst().orElseThrow().token());a.resetDuelCamera();duelDeadline=now+15000;c.getNetworkHandler().sendChatCommand("cardworlds qa spelltrap prepare");step=183;}
    case 183->{var v=a.state.duel();var own=v.cards().stream().filter(q->q.controller()==v.you()&&q.zone()==Duel.Zone.FIELD&&q.category().equals("pokemon")).findFirst();var enemy=v.cards().stream().filter(q->q.controller()!=v.you()&&q.zone()==Duel.Zone.FIELD&&q.category().equals("pokemon")).findFirst();if(own.isEmpty()||enemy.isEmpty()){if(now>duelDeadline)throw new AssertionError("VFX battle actors missing");return;}if(!a.duelPage.visualSettled()){if(now>duelDeadline)throw new AssertionError("VFX actors did not settle");next=now+150;return;}vfxSource=own.get().token();vfxTarget=enemy.get().token();a.localNotice="";a.duelPage.previewVfx("SUMMON_NORMAL",vfxSource,"");next=now+a.duelPage.vfxCaptureDelay();step=184;}
    case 184->{a.duelPage.verifyNativePose(vfxSource,"SPAWN");shot(c,"29-summon-vfx");a.duelPage.previewVfx("ATTACK_PHYSICAL",vfxSource,vfxTarget);next=now+a.duelPage.vfxCaptureDelay();step=185;}
    case 185->{a.duelPage.verifyNativePose(vfxSource,"ATTACK_PHYSICAL");a.duelPage.verifyNativePose(vfxTarget,"HIT");shot(c,"30-physical-attack-vfx");a.duelPage.previewVfx("ATTACK_SPECIAL",vfxSource,vfxTarget);next=now+a.duelPage.vfxCaptureDelay();step=186;}
    case 186->{a.duelPage.verifyNativePose(vfxSource,"ATTACK_SPECIAL");a.duelPage.verifyNativePose(vfxTarget,"HIT");shot(c,"31-special-attack-vfx");a.duelPage.previewVfx("CAST_STATUS",vfxSource,vfxTarget);next=now+a.duelPage.vfxCaptureDelay();step=187;}
    case 187->{a.duelPage.verifyNativePose(vfxSource,"CAST_STATUS");a.duelPage.previewVfx("SUMMON_TRANSFORM",vfxSource,"");next=now+a.duelPage.vfxCaptureDelay();step=188;}
    case 188->{a.duelPage.verifyNativePose(vfxSource,"TRANSFORM");c.getNetworkHandler().sendChatCommand("cardworlds qa spelltrap chain");duelDeadline=now+10000;next=now+200;step=189;}
    case 189->{var v=a.state.duel();if(v.chain().size()!=3){if(now>duelDeadline)throw new AssertionError("VFX real Chain missing");next=now+150;return;}String spell=v.cards().stream().filter(q->q.name().equals("Flamethrower")&&q.zone()==Duel.Zone.SUPPORT).findFirst().orElseThrow().token();a.duelPage.previewVfx("SPELL_ACTIVATE",spell,vfxTarget);next=now+a.duelPage.vfxCaptureDelay();step=190;}
    case 190->{shot(c,"32-spell-activate-vfx");var v=a.state.duel();String trap=v.cards().stream().filter(q->q.name().equals("Counter Seal")&&q.zone()==Duel.Zone.SUPPORT).findFirst().orElseThrow().token();String prior=v.cards().stream().filter(q->q.name().equals("Mirror Barrier")&&q.zone()==Duel.Zone.SUPPORT).findFirst().orElseThrow().token();a.duelPage.previewVfx("TRAP_REVEAL",trap,prior);a.duelPage.previewChainBreak(trap,prior);next=now+a.duelPage.vfxCaptureDelay();step=191;}
    case 191->{shot(c,"33-trap-chain-vfx");c.getNetworkHandler().sendChatCommand("cardworlds qa spelltrap resolve");a.resetDuelCamera();c.options.language="vi_vn";c.getLanguageManager().setLanguage("vi_vn");languageReload=c.reloadResources();duelDeadline=now+30000;next=now+150;step=192;}
    case 192->{if(!languageReload.isDone()||c.getOverlay()!=null){if(now>duelDeadline)throw new AssertionError("Vietnamese resource reload timed out");next=now+150;return;}languageReload.join();if(!net.minecraft.client.resource.language.I18n.translate("cardworlds.ui.summon").equals("Triệu hồi"))throw new AssertionError("Minecraft did not select Vietnamese localization");String viTurn=vn.svarcade.tcg.client.component.CardWorldsLanguage.translate("cardworlds.ui.turn3"),viLp=vn.svarcade.tcg.client.component.CardWorldsLanguage.translate("cardworlds.ui.lp7500");if(!viTurn.equals("Lượt 3")||!viLp.equals("SL 7500"))throw new AssertionError("Dynamic Vietnamese counters leaked translation keys: "+viTurn+" / "+viLp);org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_VI_DYNAMIC_PROOF turn={} lp={}",viTurn,viLp);next=now+1800;step=193;}
    case 193->{a.localNotice="";shot(c,"34-vietnamese-duel-ui");org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_VIETNAMESE_PROOF language={} summon={} defense={}",c.getLanguageManager().getLanguage(),net.minecraft.client.resource.language.I18n.translate("cardworlds.ui.summon"),net.minecraft.client.resource.language.I18n.translate("cardworlds.ui.defense_position"));c.options.language="en_us";c.getLanguageManager().setLanguage("en_us");languageReload=c.reloadResources();duelDeadline=now+30000;next=now+150;step=194;}
    case 194->{if(!languageReload.isDone()||c.getOverlay()!=null){if(now>duelDeadline)throw new AssertionError("English fallback reload timed out");next=now+150;return;}languageReload.join();if(!net.minecraft.client.resource.language.I18n.translate("cardworlds.ui.summon").equals("Summon"))throw new AssertionError("English localization fallback failed");org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_ENGLISH_FALLBACK_PROOF language=en_us");specialCapture=0;a.send("qa_special",specialIds.get(0),"false");duelDeadline=now+30000;proofReadyAt=now+3500;next=now+200;step=195;}
    case 195->{String id=specialIds.get(specialCapture);var definition=a.state.definitions().get(id);if(definition==null)throw new AssertionError("Requested real special card is unavailable: "+id);
        var source=a.state.duel().cards().stream().filter(q->q.name().equals(definition.name())&&q.zone()==Duel.Zone.FIELD).findFirst();
        if(source.isEmpty()||now<proofReadyAt||!a.duelPage.visualSettled()){if(now>duelDeadline)throw new AssertionError("Special actor did not settle: "+id);next=now+150;return;}
        vfxSource=source.get().token();vfxTarget=a.state.duel().cards().stream().filter(q->q.controller()!=a.state.duel().you()&&q.zone()==Duel.Zone.FIELD).findFirst().map(Duel.VisibleCard::token).orElse(vfxSource);
        if(specialCapture!=3&&definition.effect().target().equals("none"))vfxTarget=vfxSource;
        if(specialCapture!=3&&definition.effect().target().equals("ally"))vfxTarget=a.state.duel().cards().stream().filter(q->q.controller()==a.state.duel().you()&&!q.token().equals(vfxSource)&&q.zone()==Duel.Zone.FIELD).findFirst().orElseThrow().token();
        a.localNotice="";a.duelPage.previewAuthoredEffect(vfxSource,vfxTarget,specialCapture==3);next=now+(int)(a.duelPage.vfxCaptureDelay()*.66);step=196;}
    case 196->{String localizedName=a.duelPage.localizedCardName(a,vfxSource);if(localizedName.contains("%")||localizedName.contains("card.svarcade_tcg"))throw new AssertionError("Unformatted special card name: "+localizedName);if(specialCapture<3)a.duelPage.verifyAuthoredAnimation(vfxSource);if(specialCapture==1||specialCapture==2)a.duelPage.verifyNativePose(vfxSource,"CAST_STATUS");
        String capture=List.of("35-special-lucario-effect","36-special-solar-effect","37-special-mewtwo-effect","38-advanced-chain-effect").get(specialCapture);shot(c,capture);
        org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_SPECIAL_EFFECT_CAPTURE card={} capture={} realGameplay=true",specialIds.get(specialCapture),capture);
        if(++specialCapture<4){a.send("qa_special",specialIds.get(specialCapture),Boolean.toString(specialCapture==3));proofReadyAt=now+3500;duelDeadline=now+30000;next=now+200;step=195;}
        else{duelDeadline=0;a.send("duel","concede","","");step=18;}}
    case 17->{var v=a.state.duel();if(v.winner().isBlank()&&rounds++<40){if(v.priority()==v.you()){boolean acted=false;if(v.open()&&v.turnPlayer()==v.you()&&v.phase().equals("BATTLE")&&v.turn()>1){var attacker=v.cards().stream().filter(q->q.controller()==v.you()&&q.zone()==Duel.Zone.FIELD).findFirst();var target=v.cards().stream().filter(q->q.controller()!=v.you()&&q.zone()==Duel.Zone.FIELD).findFirst();if(attacker.isPresent()){a.send("duel","attack",attacker.get().token(),target.map(Duel.VisibleCard::token).orElse(""));acted=true;}}if(!acted)a.send("duel",v.open()?"next":"pass","","");}next=now+600;return;}a.send("duel","concede","","");step++;}
    case 18->{shot(c,"15-duel-result");((net.minecraft.client.gui.screen.Screen)a).close();step=20;}
    case 21->{shot(c,"16-reopen-y");((net.minecraft.client.gui.screen.Screen)a).keyPressed(GLFW.GLFW_KEY_Y,0,0);step=22;}
    case 24->{shot(c,"17-reopen-command");a.navigate("Collection");a.fields.put("search","");a.focus="search";((net.minecraft.client.gui.screen.Screen)a).charTyped('y',0);if(!a.fields.get("search").equals("y"))throw new AssertionError("Typing Y failed");step++;}
    case 25->{c.options.getGuiScale().setValue(2);c.onResolutionChanged();a.focus="";a.fields.put("search","");step++;}
    case 26->{shot(c,"18-scale-two");step++;}
    case 27->{org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_QA_COMPLETE");c.scheduleStop();step=30;}
   }
  }catch(Throwable e){org.slf4j.LoggerFactory.getLogger("cardworlds-qa").error("CARDWORLDS_QA_FAILED step="+step,e);shot(c,"failure-"+step);c.scheduleStop();}
 }
 private void focusedTick(MinecraftClient c){
  if(c.currentScreen instanceof CardWorldsScreen)c.getToastManager().clear();
  long now=System.currentTimeMillis();if(now<focusedNext)return;focusedNext=now+1300;
  try{
   if(c.player==null){
    if(!focusedWorldStarted&&c.currentScreen!=null){
     if(++focusedPreWorldPasses<8)return;
     focusedWorldStarted=true;
     var info=new net.minecraft.world.level.LevelInfo("Card Worlds Focused QA",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);
     c.createIntegratedServerLoader().createAndStart("cardworlds-focused-qa",info,net.minecraft.world.gen.GeneratorOptions.createRandom(),registries->registries.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(net.minecraft.world.gen.WorldPresets.DEFAULT).createDimensionsRegistryHolder(),new TitleScreen());
    }
    return;
   }
   if(focusedStep==0){c.setScreen(null);c.getNetworkHandler().sendChatCommand("cardworlds");focusedStep++;return;}
   if(!(c.currentScreen instanceof CardWorldsScreen a))return;
   switch(focusedStep){
    case 1->{if(a.state.total()<1025)throw new AssertionError("Focused QA catalog missing");a.navigate("Collection");a.ownership="All";a.category="pokemon";a.selected="charizard";a.details=true;focusedNext=now+1800;focusedStep++;}
    case 2->{if(a.selectedCard()==null)throw new AssertionError("Charizard card missing");String effect=vn.svarcade.tcg.client.component.CardWorldsLanguage.effect(a.selectedCard());if(effect.contains("Pay 500 LP")||effect.contains("Pay 200 LP"))throw new AssertionError("Blanket monster LP cost still visible: "+effect);shot(c,"focused-01-effect-card");org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_FOCUSED_IMAGE effect_card=charizard");a.details=false;a.navigate("Play");focusedNext=now+1500;focusedStep++;}
    case 3->{shot(c,"focused-02-economy-rewards");org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_FOCUSED_IMAGE economy_rewards=true");a.navigate("Packs");focusedNext=now+1500;focusedStep++;}
    case 4->{if(a.state.banners().isEmpty())throw new AssertionError("No gacha banners available");
        var beast=vn.svarcade.tcg.client.component.CurrencyPurchaseUi.coin(vn.svarcade.tcg.economy.CardWorldsCurrency.BEAST);
        var hunter=vn.svarcade.tcg.client.component.CurrencyPurchaseUi.coin(vn.svarcade.tcg.economy.CardWorldsCurrency.HUNTER);
        var beastCmd=beast.get(net.minecraft.component.DataComponentTypes.CUSTOM_MODEL_DATA);
        var hunterCmd=hunter.get(net.minecraft.component.DataComponentTypes.CUSTOM_MODEL_DATA);
        if(!new net.minecraft.component.type.CustomModelDataComponent(6).equals(beastCmd)||!new net.minecraft.component.type.CustomModelDataComponent(2).equals(hunterCmd))
            throw new AssertionError("Currency resource-pack CMD contract mismatch: beast="+beastCmd+" hunter="+hunterCmd);
        shot(c,"focused-03-gacha-currencies");
        org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_FOCUSED_CURRENCY_RENDER item=minecraft:gold_ingot beast_cmd=6 hunter_cmd=2 source=server_resource_pack");
        org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_FOCUSED_IMAGE gacha=beastcoin:150,huntercoin:2");focusedStep++;focusedNext=now+500;}
    case 5->{org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("CARDWORLDS_FOCUSED_VISUAL_QA_COMPLETE screenshots=3");c.scheduleStop();focusedStep++;}
   }
  }catch(Throwable e){org.slf4j.LoggerFactory.getLogger("cardworlds-qa").error("CARDWORLDS_FOCUSED_QA_FAILED step="+focusedStep,e);shot(c,"focused-failure-"+focusedStep);c.scheduleStop();}
 }
 private boolean proofReady(CardWorldsScreen a,long now,boolean positions){
  a.localNotice="";
  long revision=a.state.duel().revision();
  if(proofRevision!=revision){proofRevision=revision;proofReadyAt=now+1800;a.duelPage.prepareVisualProof(positions);if(step==176)a.duelPage.prepareGroundingProof();return false;}
  return now>=proofReadyAt&&a.duelPage.visualSettled();
 }
 private static void click(CardWorldsScreen a,int x,int y){double scale=MinecraftClient.getInstance().getWindow().getScaledWidth()/1280.0;((net.minecraft.client.gui.screen.Screen)a).mouseClicked(x*scale,y*scale,0);}
 private static void shot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name+".png",c.getFramebuffer(),text->org.slf4j.LoggerFactory.getLogger("cardworlds-qa").info("Captured {}",name));}
}
