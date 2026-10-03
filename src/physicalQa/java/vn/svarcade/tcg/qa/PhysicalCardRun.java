package vn.svarcade.tcg.qa;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.loot.context.*;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import vn.svarcade.tcg.physical.*;
import vn.svarcade.tcg.client.render.PhysicalCardRenderer;
import vn.svarcade.tcg.client.CardWorldsScreen;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Authoritative assertions on a real integrated server, actual interaction packets, remapped production JAR. */
public final class PhysicalCardRun implements ClientModInitializer {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("cardworlds-physical-qa");
    private int step,boots,tries;private long next,deadline;private boolean created,failedCapture,successCapture,full;
    private CompletableFuture<Void> work;private Throwable failure;
    private volatile PokemonEntity target;private volatile EmptyPokeBallEntity ball;
    private volatile int targetId,blankBefore,ownedBefore;private volatile ItemStack captured=ItemStack.EMPTY,stale=ItemStack.EMPTY;
    private long pops;
    @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);LOG.info("CARDWORLDS_PHYSICAL_QA_DRIVER_LOADED");}
    private void tick(MinecraftClient c){
        long now=System.currentTimeMillis();if(now<next)return;next=now+200;
        try {
            if(failure!=null)throw new AssertionError("Server physical QA assertion failed",failure);
            if(work!=null){if(!work.isDone())return;work.join();work=null;}
            if(c.player==null){
                if(!created&&c.currentScreen!=null&&++boots>30){created=true;
                    var info=new net.minecraft.world.level.LevelInfo("Physical Card QA",net.minecraft.world.GameMode.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);
                    c.createIntegratedServerLoader().createAndStart("physical-card-qa",info,net.minecraft.world.gen.GeneratorOptions.createRandom(),r->r.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder(),new TitleScreen());}
                return;
            }
            switch(step){
                case 0->{c.setScreen(null);c.options.getGuiScale().setValue(2);c.onResolutionChanged();command(c,"cardworlds grantblank "+c.player.getName().getString()+" 64");step=1;next=now+1800;}
                case 1->{check(c.player.getMainHandStack().isOf(CardItems.BLANK_CARD),"grantblank did not put Blank Cards in the real inventory");check(c.player.getMainHandStack().getCount()==64,"Blank stack count");
                    run(c,p->{p.getServerWorld().setTimeOfDay(6000);p.getServerWorld().getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false,p.getServer());
                        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)p.getServerWorld().setBlockState(new BlockPos(x,120,z),net.minecraft.block.Blocks.STONE.getDefaultState());
                        p.teleport(p.getServerWorld(),.5,121,.5,0,0);check(CardItems.PHYSICAL_CARD.getMaxCount()==1&&CardItems.BLANK_CARD.getMaxCount()==64,"principal item max count");
                        ownedBefore=PhysicalCards.catalog()==null?-1:0;
                        invalidTargets(p);
                    });step=2;next=now+2200;}
                case 2->{shot(c,"physical-01-blank-held");c.setScreen(new InventoryScreen(c.player));step=3;next=now+1000;}
                case 3->{shot(c,"physical-02-blank-inventory");c.setScreen(null);mark("CARDWORLDS_PHYSICAL_CARD_ITEM_PASS");
                    // A forged ID packet has no redeem action/receiver and cannot mint a copy.
                    vn.svarcade.tcg.fabric.TcgClient.request("redeem",List.of("charizard"),0);step=4;next=now+500;}
                case 4->{run(c,p->{check(storeOwned(p)==0,"client ID packet minted a collection copy");spawn(p,"mewtwo",false);});step=5;next=now+1500;}
                case 5->{var e=c.world.getEntityById(targetId);if(e==null)return;c.setScreen(null);shot(c,"physical-03-target-pokemon");
                    c.interactionManager.interactEntity(c.player,e,Hand.MAIN_HAND);deadline=now+35000;step=6;next=now+1000;}
                case 6->{run(c,p->{check(blankCount(p)==blankBefore-1,"valid attempt must consume exactly one Blank Card");
                        ball=p.getServerWorld().getEntitiesByClass(EmptyPokeBallEntity.class,p.getBoundingBox().expand(10),b->b.getCapturingPokemon()==target).stream().findFirst().orElse(null);
                        check(ball!=null||target.isRemoved(),"capture pipeline did not start");});step=7;next=now+1000;}
                case 7->{if(ball!=null&&!ball.getCaptureFuture().isDone()){if(now>deadline)throw new AssertionError("Real capture deadline expired");return;}
                    run(c,p->{boolean success=ball!=null&&Boolean.TRUE.equals(ball.getCaptureFuture().getNow(false));check(blankCount(p)==blankBefore-1,"attempt consumed multiple Blank Cards");
                        if(!success){check(target.isAlive()&&target.getPokemon().isWild(),"failed capture removed Pokémon");check(physicalCount(p)==0,"failed capture minted reward");failedCapture=true;mark("CARDWORLDS_BLANK_CAPTURE_FAILURE_PASS");target.discard();}
                        else {captured=findPhysical(p);verifyCaptured(p,captured);successCapture=true;
                            // A lucky real Mewtwo catch is valid; clear that QA reward before observing a failure.
                            for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(CardItems.PHYSICAL_CARD))p.getInventory().setStack(i,ItemStack.EMPTY);
                            target.discard();p.currentScreenHandler.sendContentUpdates();}
                    });step=8;next=now+400;}
                case 8->{if(!failedCapture){if(++tries>15)throw new AssertionError("No failure observed in real basic-ball attempts");run(c,p->spawn(p,"mewtwo",false));step=5;next=now+900;return;}
                    run(c,p->spawn(p,"magikarp",true));step=9;next=now+1300;}
                case 9->{var e=c.world.getEntityById(targetId);if(e==null)return;c.interactionManager.interactEntity(c.player,e,Hand.MAIN_HAND);deadline=now+35000;step=10;next=now+1000;}
                case 10->{run(c,p->{check(blankCount(p)==blankBefore-1,"success attempt Blank Card count");ball=p.getServerWorld().getEntitiesByClass(EmptyPokeBallEntity.class,p.getBoundingBox().expand(10),b->b.getCapturingPokemon()==target).stream().findFirst().orElse(null);check(ball!=null,"success capture not started");});step=11;}
                case 11->{if(!ball.getCaptureFuture().isDone()){if(now>deadline)throw new AssertionError("success capture timed out");return;}
                    if(!Boolean.TRUE.equals(ball.getCaptureFuture().getNow(false))){if(++tries>30)throw new AssertionError("No successful real capture");run(c,p->{check(target.isAlive(),"failure target vanished");target.discard();spawn(p,"magikarp",true);});step=9;next=now+900;return;}
                    run(c,p->{check(blankCount(p)==blankBefore-1,"successful capture consumption");captured=findPhysical(p).copy();verifyCaptured(p,captured);successCapture=true;
                        int before=physicalCount(p);BlankCapture.seal(ball,target,p,target.getPokemon());check(physicalCount(p)==before,"duplicate callback produced another reward");
                    });step=12;next=now+1000;}
                case 12->{check(c.world.getEntityById(targetId)==null,"captured Pokémon still exists on the client");c.setScreen(new InventoryScreen(c.player));step=121;next=now+1000;}
                case 121->{shot(c,"physical-04-captured-card");c.setScreen(null);
                    run(c,p->{p.getInventory().setStack(0,captured.copy());p.getInventory().selectedSlot=0;for(int i=1;i<p.getInventory().size();i++)p.getInventory().setStack(i,ItemStack.EMPTY);p.getInventory().markDirty();p.currentScreenHandler.sendContentUpdates();});step=13;next=now+1400;}
                case 13->{check(c.player.getMainHandStack().isOf(CardItems.PHYSICAL_CARD),"physical card not held");shot(c,"physical-05-card-held");pops=PhysicalCardRenderer.popCount();
                    run(c,p->{stale=p.getMainHandStack().copy();ownedBefore=storeOwned(p);});step=14;next=now+400;}
                case 14->{c.interactionManager.interactItem(c.player,Hand.MAIN_HAND);step=15;deadline=now+6000;next=now+300;}
                case 15->{if(PhysicalCardRenderer.popCount()==pops){if(now>deadline)throw new AssertionError("No floating-item pop payload executed");return;}step=151;next=now+500;}
                case 151->{shot(c,"physical-06-redeem-pop");mark("CARDWORLDS_REDEEM_POP_PASS");
                    run(c,p->{check(p.getMainHandStack().isEmpty(),"redeem did not consume held item");check(storeOwned(p)==ownedBefore+1,"redeem count must increase once");mark("CARDWORLDS_PHYSICAL_REDEEM_PASS");
                        p.setStackInHand(Hand.MAIN_HAND,stale.copy());check(PhysicalCards.redeem(p,Hand.MAIN_HAND),"stale stack did not recover");check(p.getMainHandStack().isEmpty(),"recovered stale stack not consumed");check(storeOwned(p)==ownedBefore+1,"recovered stack minted twice");
                        var d=stale.get(CardItems.DATA);var denied=store().redeemPhysical(UUID.randomUUID().toString(),d.physicalId(),d.cardId(),d.finish(),d.origin(),d.finderUuid());check(!denied.accepted()&&storeOwned(p)==ownedBefore+1,"other player minted repeated token");mark("CARDWORLDS_PHYSICAL_REDEEM_IDEMPOTENT_PASS");
                        loot(p);p.getInventory().setStack(0,new ItemStack(CardItems.BLANK_CARD,16));for(int i=1;i<36;i++)p.getInventory().setStack(i,new ItemStack(Items.STONE,64));p.currentScreenHandler.sendContentUpdates();full=true;spawn(p,"magikarp",true);
                    });step=16;next=now+1500;}
                case 16->{var e=c.world.getEntityById(targetId);if(e==null)return;c.interactionManager.interactEntity(c.player,e,Hand.MAIN_HAND);deadline=now+35000;step=17;next=now+1000;}
                case 17->{run(c,p->{check(blankCount(p)==blankBefore-1,"full inventory capture consumption");ball=p.getServerWorld().getEntitiesByClass(EmptyPokeBallEntity.class,p.getBoundingBox().expand(10),b->b.getCapturingPokemon()==target).stream().findFirst().orElseThrow();});step=18;}
                case 18->{if(!ball.getCaptureFuture().isDone()){if(now>deadline)throw new AssertionError("full inventory capture timed out");return;}
                    if(!Boolean.TRUE.equals(ball.getCaptureFuture().getNow(false))){if(++tries>50)throw new AssertionError("full inventory capture never succeeded");run(c,p->{target.discard();spawn(p,"magikarp",true);});step=16;next=now+900;return;}
                    run(c,p->{var rewards=p.getServerWorld().getEntitiesByClass(ItemEntity.class,p.getBoundingBox().expand(8),e->e.getStack().isOf(CardItems.PHYSICAL_CARD));check(rewards.size()==1,"full inventory did not drop exactly one reward");verifyNoStorage(p);check(storeOwned(p)==ownedBefore+1,"capture incorrectly entered collection");mark("CARDWORLDS_BLANK_FULL_INVENTORY_DROP_PASS");for(int i=0;i<p.getInventory().size();i++)p.getInventory().setStack(i,ItemStack.EMPTY);p.currentScreenHandler.sendContentUpdates();});step=19;next=now+1000;}
                case 19->{command(c,"cardworlds");step=20;deadline=now+10000;next=now+1300;}
                case 20->{if(!(c.currentScreen instanceof CardWorldsScreen screen)){if(now>deadline)throw new AssertionError("collection screen did not open");return;}screen.navigate("Collection");screen.selected=captured.get(CardItems.DATA).cardId();screen.details=true;check(screen.count(screen.selected)>=1,"collection snapshot missing redeemed identity");step=21;next=now+1500;}
                case 21->{shot(c,"physical-07-collection");check(failedCapture&&successCapture,"missing real capture branches");mark("CARDWORLDS_BLANK_CAPTURE_PASS");mark("CARDWORLDS_PHYSICAL_CARD_RUNTIME_QA_PASS");step=22;c.scheduleStop();}
            }
        }catch(Throwable e){LOG.error("CARDWORLDS_PHYSICAL_CARD_RUNTIME_QA_FAILED step="+step,e);shot(c,"physical-failure-"+step);step=99;c.scheduleStop();}
    }
    private void run(MinecraftClient c,Consumer<ServerPlayerEntity> task){work=new CompletableFuture<>();c.getServer().execute(()->{try{task.accept(c.getServer().getPlayerManager().getPlayer(c.player.getUuid()));work.complete(null);}catch(Throwable e){failure=e;work.completeExceptionally(e);}});}
    private static vn.svarcade.tcg.economy.CardStore store(){return PhysicalCards.store();}
    private static int storeOwned(ServerPlayerEntity p){return store().profile(p.getUuidAsString()).owned();}
    private static int blankCount(ServerPlayerEntity p){return p.getMainHandStack().isOf(CardItems.BLANK_CARD)?p.getMainHandStack().getCount():0;}
    private static int physicalCount(ServerPlayerEntity p){int n=0;for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(CardItems.PHYSICAL_CARD))n+=p.getInventory().getStack(i).getCount();return n;}
    private static ItemStack findPhysical(ServerPlayerEntity p){for(int i=0;i<p.getInventory().size();i++){var s=p.getInventory().getStack(i);if(s.isOf(CardItems.PHYSICAL_CARD))return s;}throw new AssertionError("successful capture did not produce a physical card");}
    private void spawn(ServerPlayerEntity p,String species,boolean easy){
        var pokemon=PokemonProperties.Companion.parse(species+" level=62").create(null);if(easy)pokemon.setCurrentHealth(1);
        target=new PokemonEntity(p.getServerWorld(),pokemon,CobblemonEntities.POKEMON);target.setPosition(.5,121,3.5);target.setAiDisabled(true);
        check(p.getServerWorld().spawnEntity(target),"spawn failed");targetId=target.getId();ball=null;blankBefore=blankCount(p);
    }
    private void invalidTargets(ServerPlayerEntity p){
        int before=blankCount(p);spawn(p,"magikarp",true);target.discard();check(!BlankCapture.attempt(p,target,Hand.MAIN_HAND)&&blankCount(p)==before,"removed target consumed blank");
        spawn(p,"magikarp",true);com.cobblemon.mod.common.pokemon.properties.UncatchableProperty.INSTANCE.uncatchable().apply(target.getPokemon());check(!BlankCapture.attempt(p,target,Hand.MAIN_HAND)&&blankCount(p)==before,"uncatchable consumed blank");target.discard();
        spawn(p,"magikarp",true);PlayerPartyStore other=new PlayerPartyStore(UUID.randomUUID());other.add(target.getPokemon());check(!target.getPokemon().isWild(),"owned fixture still wild");check(!BlankCapture.attempt(p,target,Hand.MAIN_HAND)&&blankCount(p)==before,"another player's Pokémon captured");other.remove(target.getPokemon());target.discard();
        ItemStack bad=new ItemStack(CardItems.PHYSICAL_CARD);p.setStackInHand(Hand.MAIN_HAND,bad);check(!PhysicalCards.redeem(p,Hand.MAIN_HAND),"missing component accepted");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(CardItems.BLANK_CARD,before));
        // Real Pokémon identity resolution against the FINAL provider-hydrated catalog.
        for(String species:List.of("mew","arceus","magikarp")){
            var live=PokemonProperties.Companion.parse(species).create(null);
            check(PhysicalCards.identities().resolve(live.getSpecies().getResourceIdentifier().toString(),live.getAspects()).orElseThrow().id().equals(species),"canonical species base: "+species);
        }
        var regional=PhysicalCards.catalog().cards().values().stream().filter(card->card.aspects().contains("alolan")).findFirst().orElseThrow();
        var form=PokemonProperties.Companion.parse(regional.species()).create(null);form.setForcedAspects(new HashSet<>(regional.aspects()));form.updateAspects();
        check(PhysicalCards.identities().resolve(form.getSpecies().getResourceIdentifier().toString(),form.getAspects()).orElseThrow().aspects().contains("alolan"),"regional live identity");
        var special=PhysicalCards.catalog().cards().get("special_lucario_mega");if(special!=null){
            var live=PokemonProperties.Companion.parse(special.species()).create(null);live.setForcedAspects(new HashSet<>(special.aspects()));live.updateAspects();
            check(PhysicalCards.identities().resolve(live.getSpecies().getResourceIdentifier().toString(),live.getAspects()).orElseThrow().id().equals(special.id()),"live Mega/provider identity");
        }
        mark("CARDWORLDS_LIVE_BASE_FORM_PROVIDER_IDENTITY_PASS");
        // Official provider veto must not consume the card or refund a real Poké Ball.
        spawn(p,"magikarp",true);
        var subscription=com.cobblemon.mod.common.api.events.CobblemonEvents.THROWN_POKEBALL_HIT.subscribe((java.util.function.Consumer<com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent>)event->{if(event.getPokemon()==target)event.cancel();});
        try {check(!BlankCapture.attempt(p,target,Hand.MAIN_HAND)&&blankCount(p)==before,"provider veto consumed a card");check(p.getServerWorld().getEntitiesByClass(ItemEntity.class,p.getBoundingBox().expand(10),item->item.getStack().isOf(com.cobblemon.mod.common.CobblemonItems.POKE_BALL)).isEmpty(),"provider veto refunded a real Poké Ball");}
        finally{subscription.unsubscribe();target.discard();}
        var sample=PhysicalCards.create("charizard","Normal","ADMIN_GRANT",p);
        var data=sample.get(CardItems.DATA);var bytes=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),p.getRegistryManager());
        try{PhysicalCardData.PACKET_CODEC.encode(bytes,data);check(data.equals(PhysicalCardData.PACKET_CODEC.decode(bytes)),"component packet round trip failed");}
        finally{bytes.release();}
        var forged=new PhysicalCardData(1,UUID.randomUUID(),"unregistered_card","Normal","ADMIN_GRANT","","","",List.of(),0,false,System.currentTimeMillis());
        p.setStackInHand(Hand.MAIN_HAND,CardItems.physical(forged));check(!PhysicalCards.redeem(p,Hand.MAIN_HAND)&&storeOwned(p)==0,"unknown held card ID was accepted");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(CardItems.BLANK_CARD,before));
        for(var entity:p.getServerWorld().getEntitiesByClass(PokemonEntity.class,p.getBoundingBox().expand(10),entity->true))entity.discard();
        mark("CARDWORLDS_PHYSICAL_SECURITY_PASS");
    }
    private void verifyCaptured(ServerPlayerEntity p,ItemStack reward){
        var d=reward.get(CardItems.DATA);check(d!=null&&reward.getCount()==1,"invalid captured stack");d.validate(PhysicalCards.catalog());
        check(d.origin().equals("BLANK_CAPTURE")&&d.finderUuid().equals(p.getUuidAsString())&&d.capturedLevel()==62,"capture provenance mismatch");
        check(d.capturedSpecies().equals(target.getPokemon().getSpecies().getResourceIdentifier().toString()),"captured species mismatch");
        check(new TreeSet<>(d.capturedAspects()).equals(new TreeSet<>(PhysicalCards.identities().relevantAspects(d.capturedSpecies(),target.getPokemon().getAspects()))),"captured aspects mismatch");
        check(d.cardId().equals(PhysicalCards.identities().resolve(d.capturedSpecies(),d.capturedAspects()).orElseThrow().id()),"resolved identity mismatch");
        check(physicalCount(p)==1&&storeOwned(p)==0,"capture produced multiple physical cards or a collection card");verifyNoStorage(p);mark("CARDWORLDS_CAPTURE_IDENTITY_PASS");
    }
    private void verifyNoStorage(ServerPlayerEntity p){var id=target.getPokemon().getUuid();check(target.isRemoved()&&p.getServerWorld().getEntity(target.getUuid())==null,"captured entity still in world");check(p.getServerWorld().getEntitiesByClass(PokemonEntity.class,p.getBoundingBox().expand(16),e->e.getPokemon().getUuid().equals(id)).isEmpty(),"duplicate world Pokémon UUID");check(PlayerExtensionsKt.party(p).get(id)==null&&PlayerExtensionsKt.pc(p).get(id)==null,"captured UUID remains in party/PC");mark("CARDWORLDS_CAPTURE_NO_STORAGE_DUPLICATE_PASS");}
    private void loot(ServerPlayerEntity p){
        var key=RegistryKey.of(RegistryKeys.LOOT_TABLE,Identifier.ofVanilla("chests/simple_dungeon"));var table=p.getServer().getReloadableRegistries().getLootTable(key);
        var parameters=new LootContextParameterSet.Builder(p.getServerWorld()).add(LootContextParameters.ORIGIN,p.getPos()).build(LootContextTypes.CHEST);
        Set<UUID> tokens=new HashSet<>();int blanks=0;for(int i=1;i<=150;i++)for(var stack:table.generateLoot(parameters,i)){
            if(stack.isOf(CardItems.PHYSICAL_CARD)){var d=stack.get(CardItems.DATA);check(d!=null,"chest card missing data");d.validate(PhysicalCards.catalog());check(tokens.add(d.physicalId()),"loot duplicate token");}
            if(stack.isOf(CardItems.BLANK_CARD))blanks+=stack.getCount();
        }
        check(tokens.size()>1&&blanks>0,"configured Minecraft chest tables missing cards/blanks");LOG.info("CARDWORLDS_PHYSICAL_LOOT_PASS table=minecraft:chests/simple_dungeon physical={} blanks={} uniqueTokens={}",tokens.size(),blanks,tokens.size());
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void mark(String marker){LOG.info(marker);}
    private static void command(MinecraftClient c,String command){c.getNetworkHandler().sendChatCommand(command);}
    private static void shot(MinecraftClient c,String name){ScreenshotRecorder.saveScreenshot(c.runDirectory,name+".png",c.getFramebuffer(),t->LOG.info("Captured {}",name));}
}
