package vn.svframe.svrtp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.Files;
import java.math.*;
import java.util.List;

/** Real paid teleport into a read-only-downloaded production terrain sample, on a local server. */
final class NaturalDriver implements ModInitializer {
    int step,age,generatedDuringSearch;boolean ready,sent,ack,finished;ServerPlayer player;SVRTP mod;BigInteger before;
    final String[] slots={"resource","resource","resource","nether","nether","nether","end","end","end"};
    final java.util.ArrayList<Long> searchTicks=new java.util.ArrayList<>();boolean lastTickSearching;long tickStarted;
    public void onInitialize(){
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("svrtpqatest").then(Commands.literal("ack").then(Commands.argument("step",IntegerArgumentType.integer()).executes(c->{if(c.getSource().getPlayerOrException()==player && IntegerArgumentType.getInteger(c,"step")==step)ack=true;return 1;})))));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerTickEvents.START_SERVER_TICK.register(s->{
            tickStarted=System.nanoTime();
            lastTickSearching=mod!=null && player!=null && mod.jobs.containsKey(player.getUUID()) && player.serverLevel()==s.overworld();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_GENERATE.register((w,c)->{
            if(mod!=null && player!=null && step<slots.length && mod.jobs.containsKey(player.getUUID()) && player.serverLevel()==player.server.overworld()
                    && w.dimension().location().toString().equals(mod.config.destinations.get(slots[step]).dimension))generatedDuringSearch++;
        });
    }
    void tick(MinecraftServer s){try{
        if(lastTickSearching && searchTicks.size()<4096)searchTicks.add(System.nanoTime()-tickStarted);
        if(finished){if(++age>40)s.halt(false);return;}
        if(s.getPlayerList().getPlayers().isEmpty())return;player=s.getPlayerList().getPlayers().getFirst();
        if(!ready){if(++age<100)return;initialize(s);ready=true;age=0;}
        if(step==slots.length){java.util.Collections.sort(searchTicks);System.out.println("SVRTP_NATURAL_QA_LOCAL_TICK_SPAN samples="+searchTicks.size()+" median="+percentile(.5)+" p95="+percentile(.95)+" p99="+percentile(.99)+" max="+percentile(1));System.out.println("SVRTP_NATURAL_QA_FINISHED cases="+slots.length+" generatedDuringSearch="+generatedDuringSearch);player.sendSystemMessage(Component.literal("SVRTP_QA_FINISHED"));finished=true;age=0;return;}
        if(!sent){if(++age<60)return;player.teleportTo(s.overworld(),7.5,120,7.5,0,0);mod.cache.clear();generatedDuringSearch=0;before=Integrations.wallet(player,"beastcoin").balance();sent=true;ack=false;age=0;player.sendSystemMessage(Component.literal("SVRTP_QA_STEP "+step+" "+slots[step]+" bc"));}
        else if(ack){
            if(!mod.jobs.isEmpty()) {if(++age>500)throw new IllegalStateException("Natural RTP remained pending");return;}
            if(!player.serverLevel().dimension().location().toString().equals(mod.config.destinations.get(slots[step]).dimension))throw new IllegalStateException("Natural RTP did not complete");
            if(!Integrations.wallet(player,"beastcoin").balance().equals(before.subtract(BigInteger.valueOf(5))))throw new IllegalStateException("Natural RTP payment mismatch");
            if(!SafeLanding.origin(player.serverLevel(),player) || player.getHealth()!=player.getMaxHealth())throw new IllegalStateException("Unsafe natural arrival");
            if(!mod.jobs.isEmpty() || mod.ioPending!=0)throw new IllegalStateException("Native search did not clean up");
            if(generatedDuringSearch!=0)throw new IllegalStateException("Search activated newly generated FULL chunks: "+generatedDuringSearch);
            System.out.println("SVRTP_NATURAL_QA_PASS step="+step+" alias="+slots[step]+" position="+player.position()+" exactCharge=5 asyncLoads="+mod.loadedChunks+" candidatesRejected="+mod.rejectedCandidates+" generatedDuringSearch="+generatedDuringSearch);
            step++;sent=false;age=0;
        }else if(++age>500)throw new IllegalStateException("Natural RTP timed out");
    }catch(Exception e){System.out.println("SVRTP_NATURAL_QA_FAIL "+e);e.printStackTrace();player.sendSystemMessage(Component.literal("SVRTP_QA_FINISHED"));finished=true;age=0;}}
    double percentile(double fraction){return searchTicks.isEmpty()?Double.NaN:searchTicks.get(Math.max(0,(int)Math.ceil(fraction*searchTicks.size())-1))/1_000_000.0;}
    void initialize(MinecraftServer s)throws Exception {
        mod=(SVRTP)FabricLoader.getInstance().getEntrypoints("main",ModInitializer.class).stream().filter(m->m instanceof SVRTP).findFirst().orElseThrow();
        var c=new Config();c.resourcePackGui=true;var d=c.destinations.get("resource");d.dimension="minigamedim:minigame";
        for(var slot:c.destinations.values())slot.cooldownSeconds=0;
        Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("svrtp.json"),Config.JSON.toJson(c));s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"rtp reload");
        s.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
        // Only the source fixture is authored. Target terrain is never replaced by a test platform.
        var source=s.overworld();source.getChunk(0,0);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){source.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());for(int y=120;y<125;y++)source.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
        for(var slot:c.destinations.values()) {
            var world=s.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(slot.dimension)));
            world.getWorldBorder().setCenter(0,0);world.getWorldBorder().setSize(40000);
            var border=new org.popcraft.chunkyborder.BorderData();border.setWorld(new org.popcraft.chunky.platform.FabricWorld(world).getName());border.setCenterX(0);border.setCenterZ(0);border.setRadiusX(20000);border.setRadiusZ(20000);border.setShape("square");border.setWrap("none");org.popcraft.chunkyborder.ChunkyBorderProvider.get().getBorders().put(border.getWorld(),border);
        }
        s.getPlayerList().deop(player.getGameProfile());var user=net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(player.getUUID());for(String n:List.of("svrtp.use","svrtp.use.resource","svrtp.use.nether","svrtp.use.end"))user.data().add(net.luckperms.api.node.Node.builder(n).value(true).build());
        var api=org.krripe.beconomy.api.BEconomy.INSTANCE.getAPI();if(!api.currencyExists("beastcoin"))api.createCurrency("beastcoin","QA","minecraft:gold_nugget",0,BigDecimal.ZERO,"BC",false);api.setBalance(player.getUUID(),BigDecimal.valueOf(100),"beastcoin");
        System.out.println("SVRTP_NATURAL_QA_READY productionTerrainSample=true targetPlatformCreated=false");
    }
}
