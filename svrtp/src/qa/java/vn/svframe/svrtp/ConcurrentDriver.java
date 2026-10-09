package vn.svframe.svrtp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.*;
import java.nio.file.Files;
import java.math.*;

/** Two genuine local clients; never packaged in SVRTP's production JAR. */
final class ConcurrentDriver implements ModInitializer {
    final Set<UUID> ack=new HashSet<>();
    final Map<UUID,BigInteger> balances=new HashMap<>();
    int age,step;boolean initialized,sent,finished;
    SVRTP mod;
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("svrtpqatest").then(Commands.literal("ack").then(Commands.argument("step",IntegerArgumentType.integer()).executes(c->{
            if(IntegerArgumentType.getInteger(c,"step")==step)ack.add(c.getSource().getPlayerOrException().getUUID());return 1;})))));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }
    void tick(MinecraftServer server) {
        try {
            if(finished) {if(++age>40)server.halt(false);return;}
            if(server.getPlayerList().getPlayers().size()!=2)return;
            var players=server.getPlayerList().getPlayers();
            if(!initialized) {
                if(++age<100)return;
                mod=(SVRTP)FabricLoader.getInstance().getEntrypoints("main",ModInitializer.class).stream().filter(m->m instanceof SVRTP).findFirst().orElseThrow();
                var config=new Config();config.resourcePackGui=true;
                var target=config.destinations.get("resource");target.dimension="minigamedim:minigame";target.minRadius=0;target.maxRadius=8;target.centerX=7.5;target.centerZ=7.5;target.cooldownSeconds=600;
                Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("svrtp.json"),Config.JSON.toJson(config));
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"rtp reload");
                server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
                server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
                for(var world:server.getAllLevels()) {
                    String dimension=world.dimension().location().toString();
                    if(!dimension.equals("minecraft:overworld") && !dimension.equals("minigamedim:minigame"))continue;
                    world.getChunk(0,0);
                    for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                        world.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
                        for(int y=120;y<128;y++)world.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                    }
                    world.getWorldBorder().setCenter(7.5,7.5);world.getWorldBorder().setSize(64);
                    var border=new org.popcraft.chunkyborder.BorderData();border.setWorld(new org.popcraft.chunky.platform.FabricWorld(world).getName());border.setCenterX(7.5);border.setCenterZ(7.5);border.setRadiusX(10);border.setRadiusZ(10);border.setShape("square");border.setWrap("none");
                    org.popcraft.chunkyborder.ChunkyBorderProvider.get().getBorders().put(border.getWorld(),border);
                }
                var api=org.krripe.beconomy.api.BEconomy.INSTANCE.getAPI();
                if(!api.currencyExists("beastcoin"))api.createCurrency("beastcoin","Internal QA","minecraft:gold_nugget",0,BigDecimal.ZERO,"BC",false);
                int slot=0;
                for(var p:players) {
                    server.getPlayerList().deop(p.getGameProfile());p.teleportTo(server.overworld(),7.5,120,7.5+slot++*2,0,0);
                    var user=net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(p.getUUID());
                    for(String node:List.of("svrtp.use","svrtp.use.resource"))user.data().add(net.luckperms.api.node.Node.builder(node).value(true).build());
                    if(!Boolean.getBoolean("svrtp.qa.resume"))api.setBalance(p.getUUID(),BigDecimal.valueOf(100),"beastcoin");
                    balances.put(p.getUUID(),Integrations.wallet(p,"beastcoin").balance());
                    if(Boolean.getBoolean("svrtp.qa.resume") && mod.journal.cooldowns.getOrDefault(p.getUUID(),0L)<=System.currentTimeMillis())throw new IllegalStateException("Persisted cooldown missing after restart");
                }
                initialized=true;age=0;
                System.out.println("SVRTP_QA_CONCURRENT_READY realClients=2 resume="+Boolean.getBoolean("svrtp.qa.resume"));
            }
            if(!sent) {
                if(++age<60)return;
                if(step==1) {
                    var p=players.getFirst();
                    var party=com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(p);party.clearParty();
                    party.set(0,com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("pikachu level=10 moves=thundershock").create(p));
                    var wild=com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("magikarp level=5 moves=splash").createEntity(p.serverLevel());
                    wild.moveTo(p.getX()+2,p.getY(),p.getZ(),0,0);p.serverLevel().addFreshEntity(wild);
                    com.cobblemon.mod.common.battles.BattleBuilder.INSTANCE.pve(p,wild);
                    if(!Integrations.battling(p))throw new IllegalStateException("Native battle did not start");
                    System.out.println("SVRTP_QA_NATIVE_BATTLE_STARTED player="+p.getUUID());
                }
                for(var p:players)p.sendSystemMessage(Component.literal("SVRTP_QA_STEP "+step+" resource bc"));
                sent=true;ack.clear();age=0;
            }else if(ack.size()==2) {
                for(var p:players) {
                    boolean paid=step==0 && !Boolean.getBoolean("svrtp.qa.resume");
                    var expected=balances.get(p.getUUID()).subtract(paid?BigInteger.valueOf(5):BigInteger.ZERO);
                    if(!Integrations.wallet(p,"beastcoin").balance().equals(expected))throw new IllegalStateException("Wrong concurrent/restart debit");
                    if(paid && !p.serverLevel().dimension().location().toString().equals("minigamedim:minigame"))throw new IllegalStateException("Concurrent transfer failed");
                    balances.put(p.getUUID(),expected);
                }
                if(step==0 && !Boolean.getBoolean("svrtp.qa.resume") && mod.peakConcurrent!=2)throw new IllegalStateException("Requests did not overlap");
                System.out.println("SVRTP_QA_PASS "+(step==0?(Boolean.getBoolean("svrtp.qa.resume")?"restart-persisted-cooldowns-two-real-clients":"simultaneous-two-real-clients"):"native-cobblemon-battle-blocks-rtp")+" peak="+mod.peakConcurrent+" avgServerMspt="+server.getAverageTickTimeNanos()/1000000.0+" meanRtpLatencyMs="+(mod.completed==0?0:mod.totalLatencyMillis/mod.completed));
                if(step==1) {
                    var battle=com.cobblemon.mod.common.battles.BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(players.getFirst());
                    if(battle==null)throw new IllegalStateException("RTP interrupted the native battle");battle.stop();
                }
                if(step==1 || Boolean.getBoolean("svrtp.qa.resume")) {
                    for(var p:players)p.sendSystemMessage(Component.literal("SVRTP_QA_FINISHED"));
                    System.out.println("SVRTP_QA_FINISHED concurrent steps="+(step+1));finished=true;age=0;
                }else {step++;sent=false;age=0;}
            }else if(++age>500)throw new IllegalStateException("Concurrent client timeout");
        }catch(Exception e) {
            System.out.println("SVRTP_QA_FAIL concurrent "+e);e.printStackTrace();
            for(var p:server.getPlayerList().getPlayers())p.sendSystemMessage(Component.literal("SVRTP_QA_FINISHED"));finished=true;age=0;
        }
    }
}
