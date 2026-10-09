package vn.svframe.svrtp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import java.math.*;
import java.nio.file.*;
import java.util.*;

/** Isolated test fixture. Never included in the production JAR. */
public final class ServerDriver implements ModInitializer {
    int step,age;boolean initialized,sent,ack,finished,injected;BigInteger beforeBc,beforeCd;ServerPlayer player;float beforeHealth;
    final String[] commands={"menu","resource","nether","end","resource","resource","end","resource","spam","resource","resource","resource","resource"};
    final String[] currencies={"bc","bc","cd","cd","bc","bc","bc","bc","bc","cd","bc","bc","cd"};
    @Override public void onInitialize() {
        if(!Boolean.getBoolean("svrtp.qa.server"))return;
        if(Boolean.getBoolean("svrtp.qa.rules")) {SpawnRuleDriver.register();return;}
        if(Boolean.getBoolean("svrtp.qa.origin")) {new OriginDriver().onInitialize();return;}
        if(Boolean.getBoolean("svrtp.qa.concurrent")) {new ConcurrentDriver().onInitialize();return;}
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("svrtpqatest").then(Commands.literal("ack").then(Commands.argument("step",IntegerArgumentType.integer()).executes(c->{
            if(c.getSource().getPlayerOrException()==player && IntegerArgumentType.getInteger(c,"step")==step)ack=true;return 1;})))));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }
    void tick(MinecraftServer server) {
        try {
            if(finished) {if(++age>40)server.halt(false);return;}
            if(server.getPlayerList().getPlayers().isEmpty())return;
            player=server.getPlayerList().getPlayers().getFirst();
            if(step==10 && sent && !injected && player.serverLevel()!=server.overworld()) {
                // Real delayed position override, injected by the isolated fixture.
                player.teleportTo(player.serverLevel(),7.5,player.serverLevel().getMinBuildHeight()-5,7.5,0,0);
                injected=true;System.out.println("SVRTP_QA_INJECTED delayed-unsafe-position-override");
            }
            if(!initialized) {if(++age<100)return;initialize(server);initialized=true;age=0;}
            if(step>=commands.length) {System.out.println("SVRTP_QA_FINISHED steps="+step);player.sendSystemMessage(Component.literal("SVRTP_QA_FINISHED"));finished=true;age=0;return;}
            if(!sent) {if(++age<60)return;prepare(server);beforeBc=Integrations.wallet(player,"beastcoin").balance();beforeCd=Integrations.wallet(player,"cobbledollars").balance();
                beforeHealth=player.getHealth();player.sendSystemMessage(Component.literal("SVRTP_QA_STEP "+step+" "+commands[step]+" "+currencies[step]));sent=true;ack=false;age=0;System.out.println("SVRTP_QA_BEGIN step="+step+" nonOP="+!player.hasPermissions(2));}
            else if(ack) {verify();System.out.println("SVRTP_QA_PASS step="+step+" dimension="+player.serverLevel().dimension().location()+" bc="+Integrations.wallet(player,"beastcoin").balance()+" cd="+Integrations.wallet(player,"cobbledollars").balance());step++;sent=false;age=0;}
            else if(++age>500)throw new IllegalStateException("Step timeout "+step);
        }catch(Exception e) {System.out.println("SVRTP_QA_FAIL step="+step+" "+e);e.printStackTrace();if(player!=null)player.sendSystemMessage(Component.literal("SVRTP_QA_FINISHED"));finished=true;age=0;}
    }
    void initialize(MinecraftServer server)throws Exception {
        Config fresh=new Config();fresh.resourcePackGui=true;
        for(var entry:fresh.destinations.entrySet()) {var d=entry.getValue();d.minRadius=0;d.maxRadius=8;d.centerX=7.5;d.centerZ=7.5;d.cooldownSeconds=entry.getKey().equals("resource")?10:2;}
        fresh.destinations.get("resource").dimension="minigamedim:minigame";
        Files.writeString(FabricLoaderPath(),Config.JSON.toJson(fresh));server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"rtp reload");
        server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
        server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(var world:server.getAllLevels()) {
            int y=world.dimensionType().hasCeiling()?50:world.dimension().location().toString().equals("minecraft:the_end")?70:120;
            // Limited local fixture generation; never used on production.
            world.getChunk(0,0);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                world.setBlockAndUpdate(new BlockPos(x,y-1,z),Blocks.STONE.defaultBlockState());
                for(int dy=0;dy<8;dy++)world.setBlockAndUpdate(new BlockPos(x,y+dy,z),Blocks.AIR.defaultBlockState());
            }
            world.getWorldBorder().setCenter(7.5,7.5);world.getWorldBorder().setSize(64);
            var border=new org.popcraft.chunkyborder.BorderData();border.setWorld(new org.popcraft.chunky.platform.FabricWorld(world).getName());border.setCenterX(7.5);border.setCenterZ(7.5);border.setRadiusX(10);border.setRadiusZ(10);border.setShape("square");border.setWrap("none");
            org.popcraft.chunkyborder.ChunkyBorderProvider.get().getBorders().put(border.getWorld(),border);
        }
        player.teleportTo(server.overworld(),7.5,120,7.5,0,0);
        server.getPlayerList().deop(player.getGameProfile());
        var user=net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(player.getUUID());
        if(user==null)throw new IllegalStateException("LuckPerms did not load QA account");
        for(String node:List.of("svrtp.use","svrtp.use.resource","svrtp.use.nether","svrtp.use.end"))user.data().add(net.luckperms.api.node.Node.builder(node).value(true).build());
        var api=org.krripe.beconomy.api.BEconomy.INSTANCE.getAPI();
        if(!api.currencyExists("beastcoin"))api.createCurrency("beastcoin","Internal QA","minecraft:gold_nugget",0,BigDecimal.ZERO,"BC",false);
        api.setBalance(player.getUUID(),BigDecimal.valueOf(100),"beastcoin");
        ((fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer)player).cobbleDollars$setCobbleDollars(BigInteger.valueOf(500000));
        if(player.hasPermissions(2))throw new IllegalStateException("Expected a non-OP QA account");
        // Real block/collision validation, including void, lava, headroom and border fixtures.
        var resource=server.getAllLevels().iterator().next();
        for(var world:server.getAllLevels()) {
            int y=world.dimensionType().hasCeiling()?50:world.dimension().location().toString().equals("minecraft:the_end")?70:120;
            if(!SafeLanding.valid(world,player,7,y,7))throw new IllegalStateException("Safe platform rejected "+world.dimension().location());
            var floor=new BlockPos(7,y-1,7);world.setBlockAndUpdate(floor,Blocks.LAVA.defaultBlockState());
            if(SafeLanding.valid(world,player,7,y,7))throw new IllegalStateException("Lava accepted");world.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
            var head=new BlockPos(7,y+1,7);world.setBlockAndUpdate(head,Blocks.STONE.defaultBlockState());
            if(SafeLanding.valid(world,player,7,y,7))throw new IllegalStateException("Blocked headroom accepted");world.setBlockAndUpdate(head,Blocks.AIR.defaultBlockState());
            if(SafeLanding.valid(world,player,7,world.getMinBuildHeight(),7))throw new IllegalStateException("Void accepted");
            if(SafeLanding.valid(world,player,100,y,100))throw new IllegalStateException("Outside border accepted");
        }
        System.out.println("SVRTP_QA_PASS real-block-safety-fixtures worlds=4 hazards=void,lava,headroom,border");
        var resourceWorld=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse("minigamedim:minigame")));
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)resourceWorld.getChunk(96+dx,dz);
        for(int x=1536;x<1552;x++)for(int z=0;z<16;z++) {
            resourceWorld.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
            for(int y=120;y<128;y++)resourceWorld.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        server.saveEverything(true,true,true);
        System.out.println("SVRTP_QA_PREGNERATED_FIXTURE 5x5 FULL disk-neighbourhood centerChunk=96,0");
    }
    void prepare(MinecraftServer s)throws Exception {
        if(step>=4) {
            Path path=FabricLoaderPath();var cfg=Config.load(path);
            cfg.destinations.get("resource").dimension=step==4?"minecraft:the_nether":step==5?"missing:world":"minigamedim:minigame";
            cfg.destinations.get("resource").cooldownSeconds=2;
            if(step==7) {cfg.destinations.get("resource").minRadius=1000;cfg.destinations.get("resource").maxRadius=2000;}
            else {cfg.destinations.get("resource").minRadius=0;cfg.destinations.get("resource").maxRadius=8;}
            Files.writeString(path,Config.JSON.toJson(cfg));s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"rtp reload");
        }
        if(step==6) {var u=net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(player.getUUID());u.data().add(net.luckperms.api.node.Node.builder("svrtp.use.end").value(false).build());}
        if(step==8)player.teleportTo(s.overworld(),7.5,120,7.5,0,0);
        if(step==10) {
            s.overworld().setBlockAndUpdate(new BlockPos(7,119,7),Blocks.STONE_SLAB.defaultBlockState());
            player.teleportTo(s.overworld(),7.5,119.5,7.5,0,0);
        }
        if(step==9)s.getPlayerList().op(player.getGameProfile());
        if(step==11)Integrations.wallet(player,"beastcoin").debit(Integrations.wallet(player,"beastcoin").balance());
        if(step==12) {
            var cfg=Config.load(FabricLoaderPath());cfg.destinations.get("resource").centerX=1543.5;
            var world=s.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse("minigamedim:minigame")));
            if(world.getChunkSource().getChunkNow(96,0)!=null)throw new IllegalStateException("Async fixture center must be unloaded before request");
            world.getWorldBorder().setCenter(1543.5,7.5);
            var border=new org.popcraft.chunkyborder.BorderData();border.setWorld(new org.popcraft.chunky.platform.FabricWorld(world).getName());border.setCenterX(1543.5);border.setCenterZ(7.5);border.setRadiusX(10);border.setRadiusZ(10);border.setShape("square");border.setWrap("none");
            org.popcraft.chunkyborder.ChunkyBorderProvider.get().getBorders().put(border.getWorld(),border);
            Files.writeString(FabricLoaderPath(),Config.JSON.toJson(cfg));s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"rtp reload");
            System.out.println("SVRTP_QA_UNLOADED_CHUNK_VERIFIED centerChunk=96,0");
        }
    }
    Path FabricLoaderPath() {return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("svrtp.json");}
    void verify() {
        boolean paid=Set.of(0,2,3,4,8,9,12).contains(step);
        BigInteger bc=Integrations.wallet(player,"beastcoin").balance(),cd=Integrations.wallet(player,"cobbledollars").balance();
        if(!bc.equals(beforeBc.subtract(paid && currencies[step].equals("bc")?BigInteger.valueOf(5):BigInteger.ZERO)) || !cd.equals(beforeCd.subtract(paid && currencies[step].equals("cd")?BigInteger.valueOf(50000):BigInteger.ZERO)))throw new IllegalStateException("Wrong debit before="+beforeBc+","+beforeCd+" after="+bc+","+cd);
        String expected=switch(step) {case 0,1,8,9,12->"minigamedim:minigame";case 2,4,5,6,7->"minecraft:the_nether";case 3->"minecraft:the_end";case 10,11->"minecraft:overworld";default->"";};
        if(!player.serverLevel().dimension().location().toString().equals(expected))throw new IllegalStateException("Wrong destination expected="+expected);
        if(paid && !SafeLanding.origin(player.serverLevel(),player))throw new IllegalStateException("Unsafe arrival");
        if(step==10 && (!injected || player.getHealth()!=beforeHealth))throw new IllegalStateException("Unsafe correction was not restored without damage");
        if(step==10 && Math.abs(player.getY()-119.5)>0.001)throw new IllegalStateException("Rollback lost fractional slab source height");
        if(step==12) {
            var mod=(SVRTP)net.fabricmc.loader.api.FabricLoader.getInstance().getEntrypoints("main",ModInitializer.class).stream().filter(m->m instanceof SVRTP).findFirst().orElseThrow();
            if(mod.loadedChunks<1 || player.getX()<1536)throw new IllegalStateException("Async load did not complete");
            System.out.println("SVRTP_QA_PASS actual-async-full-chunk-load count="+mod.loadedChunks+" ioPending="+mod.ioPending);
        }
    }
}
