package vn.svframe.svrtp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.Files;
import java.math.*;
import java.util.List;

/** Native source-position regression fixtures; this driver is excluded from the release JAR. */
final class OriginDriver implements ModInitializer {
    int age,step;boolean ready,sent,ack,finished;ServerPlayer player;BigInteger before;SVRTP mod;
    final String[] cases={"chunk-edge","slab-fractional-y","carpet-fractional-y","cross-chunk-body","two-block-headroom","uneven-neighbours"};
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(Commands.literal("svrtpqatest").then(Commands.literal("ack").then(Commands.argument("step",IntegerArgumentType.integer()).executes(c->{
            if(c.getSource().getPlayerOrException()==player && IntegerArgumentType.getInteger(c,"step")==step)ack=true;return 1;})))));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }
    void tick(MinecraftServer s) {
        try {
            if(finished){if(++age>40)s.halt(false);return;}
            if(s.getPlayerList().getPlayers().isEmpty())return;
            player=s.getPlayerList().getPlayers().getFirst();
            if(!ready){if(++age<100)return;initialize(s);ready=true;age=0;}
            if(step==cases.length){System.out.println("SVRTP_ORIGIN_QA_FINISHED cases="+step);player.sendSystemMessage(Component.literal("SVRTP_QA_FINISHED"));finished=true;age=0;return;}
            if(!sent){if(++age<60)return;prepare(s);before=Integrations.wallet(player,"beastcoin").balance();ack=false;sent=true;age=0;
                player.sendSystemMessage(Component.literal("SVRTP_QA_STEP "+step+" resource bc"));System.out.println("SVRTP_ORIGIN_QA_BEGIN "+cases[step]+" source="+player.position());}
            else if(ack){
                if(!player.serverLevel().dimension().location().toString().equals("minigamedim:minigame"))throw new IllegalStateException("No native teleport for "+cases[step]);
                if(!Integrations.wallet(player,"beastcoin").balance().equals(before.subtract(BigInteger.valueOf(5))))throw new IllegalStateException("Incorrect payment");
                if(!SafeLanding.valid(player.serverLevel(),player,player.blockPosition().getX(),player.blockPosition().getY(),player.blockPosition().getZ()))throw new IllegalStateException("Unsafe destination");
                if(!mod.jobs.isEmpty())throw new IllegalStateException("Pending job leaked");
                System.out.println("SVRTP_ORIGIN_QA_PASS "+cases[step]+" nativeTeleport=true exactCharge=5");step++;sent=false;age=0;
            }else if(++age>500)throw new IllegalStateException("Origin case timed out");
        }catch(Exception e){System.out.println("SVRTP_ORIGIN_QA_FAIL step="+step+" "+e);e.printStackTrace();player.sendSystemMessage(Component.literal("SVRTP_QA_FINISHED"));finished=true;age=0;}
    }
    void initialize(MinecraftServer s)throws Exception {
        mod=(SVRTP)FabricLoader.getInstance().getEntrypoints("main",ModInitializer.class).stream().filter(m->m instanceof SVRTP).findFirst().orElseThrow();
        Config c=new Config();c.resourcePackGui=true;var d=c.destinations.get("resource");d.dimension="minigamedim:minigame";d.minRadius=0;d.maxRadius=8;d.centerX=7.5;d.centerZ=7.5;d.cooldownSeconds=0;
        Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("svrtp.json"),Config.JSON.toJson(c));s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"rtp reload");
        s.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
        for(var world:s.getAllLevels())if(List.of("minecraft:overworld","minigamedim:minigame").contains(world.dimension().location().toString())){
            world.getChunk(0,0);world.getChunk(1,0);
            for(int x=0;x<32;x++)for(int z=0;z<16;z++){world.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());for(int y=120;y<126;y++)world.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
            world.getWorldBorder().setCenter(7.5,7.5);world.getWorldBorder().setSize(64);
            var border=new org.popcraft.chunkyborder.BorderData();border.setWorld(new org.popcraft.chunky.platform.FabricWorld(world).getName());border.setCenterX(7.5);border.setCenterZ(7.5);border.setRadiusX(20);border.setRadiusZ(20);border.setShape("square");border.setWrap("none");org.popcraft.chunkyborder.ChunkyBorderProvider.get().getBorders().put(border.getWorld(),border);
        }
        s.getPlayerList().deop(player.getGameProfile());var user=net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(player.getUUID());for(String n:List.of("svrtp.use","svrtp.use.resource"))user.data().add(net.luckperms.api.node.Node.builder(n).value(true).build());
        var api=org.krripe.beconomy.api.BEconomy.INSTANCE.getAPI();if(!api.currencyExists("beastcoin"))api.createCurrency("beastcoin","QA","minecraft:gold_nugget",0,BigDecimal.ZERO,"BC",false);api.setBalance(player.getUUID(),BigDecimal.valueOf(100),"beastcoin");
        var w=s.overworld();player.teleportTo(w,7.5,120,7.5,0,0);
        if(!SafeLanding.origin(w,player))throw new IllegalStateException("Ordinary source failed");
        w.setBlockAndUpdate(new BlockPos(7,119,7),Blocks.LAVA.defaultBlockState());if(SafeLanding.origin(w,player))throw new IllegalStateException("Lava source accepted");
        w.setBlockAndUpdate(new BlockPos(7,119,7),Blocks.WATER.defaultBlockState());if(SafeLanding.origin(w,player))throw new IllegalStateException("Water source accepted");
        w.setBlockAndUpdate(new BlockPos(7,119,7),Blocks.AIR.defaultBlockState());if(SafeLanding.origin(w,player))throw new IllegalStateException("Hovering accepted");
        w.setBlockAndUpdate(new BlockPos(7,119,7),Blocks.STONE.defaultBlockState());w.setBlockAndUpdate(new BlockPos(7,120,7),Blocks.STONE.defaultBlockState());if(SafeLanding.origin(w,player))throw new IllegalStateException("Body collision accepted");w.setBlockAndUpdate(new BlockPos(7,120,7),Blocks.AIR.defaultBlockState());
        if(SafeLanding.restorable(w,player,new net.minecraft.world.phys.Vec3(10000,120,10000)))throw new IllegalStateException("Unloaded/out-of-border source accepted");
        System.out.println("SVRTP_ORIGIN_QA_PASS hazards=water,lava,hovering,body-collision,unloaded-border");
    }
    void prepare(MinecraftServer s) {
        var w=s.overworld();double x=7.5,y=120,z=7.5;
        switch(step){
            case 0->x=0.5;
            case 1->{x=15.5;y=119.5;w.setBlockAndUpdate(new BlockPos(15,119,7),Blocks.STONE_SLAB.defaultBlockState());}
            case 2->{w.setBlockAndUpdate(new BlockPos(7,120,7),Blocks.WHITE_CARPET.defaultBlockState());y=120.0625;}
            case 3->{x=15.95;w.setBlockAndUpdate(new BlockPos(15,119,7),Blocks.STONE.defaultBlockState());}
            case 4->{w.setBlockAndUpdate(new BlockPos(7,120,7),Blocks.AIR.defaultBlockState());w.setBlockAndUpdate(new BlockPos(7,122,7),Blocks.STONE.defaultBlockState());}
            case 5->{w.setBlockAndUpdate(new BlockPos(7,122,7),Blocks.AIR.defaultBlockState());w.setBlockAndUpdate(new BlockPos(8,119,7),Blocks.AIR.defaultBlockState());}
        }
        player.teleportTo(w,x,y,z,0,0);
        if(!SafeLanding.origin(w,player))throw new IllegalStateException("Source refused "+cases[step]);
        if(!SafeLanding.restorable(w,player,player.position()))throw new IllegalStateException("Exact rollback source refused");
    }
}
