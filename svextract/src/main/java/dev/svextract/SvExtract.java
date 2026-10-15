package dev.svextract;

import dev.svextract.config.ServerConfig;
import dev.svextract.mission.MissionRuntime;
import dev.svextract.network.Protocol;
import dev.svextract.compat.Integrations;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class SvExtract implements ModInitializer {
    public static final Logger LOGGER=LoggerFactory.getLogger("svextract");
    public static final String VERSION=FabricLoader.getInstance().getModContainer("svextract").orElseThrow().getMetadata().getVersion().getFriendlyString();
    private static MissionRuntime runtime;
    public static MissionRuntime runtime(){return runtime;}
    public static Path configPath(){return FabricLoader.getInstance().getConfigDir().resolve("svextract/server.json");}
    public static boolean locked(ServerPlayerEntity p){return runtime!=null&&runtime.locked(p.getUuid());}
    public static boolean restricted(ServerPlayerEntity p){return runtime!=null&&runtime.restricted(p.getUuid());}
    public static boolean isolated(World w){return w.getRegistryKey().equals(MissionRuntime.HAVEN)||w.getRegistryKey().equals(MissionRuntime.RAID)||w.getRegistryKey().equals(MissionRuntime.SKYRIM);}

    @Override public void onInitialize(){
        Content.initialize();
        try{dev.svextract.compat.SVFrameMobsAdapter.installMissionDefinition();}
        catch(Exception e){throw new IllegalStateException("Cannot install the SVFrameMobs Redline definition",e);}
        Protocol.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            try {runtime=new MissionRuntime(server,ServerConfig.load(configPath()));}
            catch(Exception e){LOGGER.error("SVEXTRACT disabled: initialization failed; inspect dimensions/configuration/database. No missions admitted.",e);}
            LOGGER.info("SVEXTRACT {} initialized; Point Blank installed: {}. Mode admission follows validated profiles and workload budgets.",VERSION,Integrations.pointBlankLoaded());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(runtime!=null){try{runtime.close();}catch(Exception e){LOGGER.error("SVEXTRACT shutdown requires journal recovery",e);}finally{runtime=null;}}});
        ServerTickEvents.START_SERVER_TICK.register(server->{if(runtime!=null)runtime.startTick();});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(runtime!=null)runtime.endTick();});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            if(runtime!=null)runtime.join(handler.player);
            else handler.disconnect(Text.literal("SVEXTRACT initialization failed. An administrator must resolve the configuration or persistence error before inventory recovery can run."));
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(dev.svextract.mission.NpcRoles.protectedService(entity)&&entity instanceof net.minecraft.entity.LivingEntity living)
                dev.svextract.mission.NpcRoles.restoreProtection(living);
            if(runtime!=null&&isolated(world))runtime.entityLoaded(entity);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{if(runtime!=null)runtime.disconnect(handler.player);});
        ServerPlayNetworking.registerGlobalReceiver(Protocol.Hello.ID,(payload,context)->{if(runtime!=null)runtime.hello(context.player(),payload);});
        ServerPlayNetworking.registerGlobalReceiver(Protocol.LobbyAction.ID,(payload,context)->{if(runtime!=null)runtime.lobbyAction(context.player(),payload);});
        ServerPlayNetworking.registerGlobalReceiver(Protocol.StashAction.ID,(payload,context)->{if(runtime!=null)runtime.stashAction(context.player(),payload);});
        ServerPlayNetworking.registerGlobalReceiver(Protocol.LootClaim.ID,(payload,context)->{if(runtime!=null)runtime.claimLoot(context.player(),payload);});
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(world.isClient || !(player instanceof ServerPlayerEntity p))return ActionResult.PASS;
            if(locked(p))return ActionResult.FAIL;
            if(entity.getCommandTags().contains("svextract_mission_control") && runtime!=null && runtime.isHub(p.getServerWorld())){
                if(p.squaredDistanceTo(entity)<=25 && hand==Hand.MAIN_HAND)runtime.openLobby(p,true);
                return ActionResult.SUCCESS;
            }
            return restricted(p)?ActionResult.FAIL:ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClient || !(player instanceof ServerPlayerEntity p))return ActionResult.PASS;
            if(locked(p))return ActionResult.FAIL;
            if(isolated(world)){
                if(runtime!=null&&hand==Hand.MAIN_HAND&&runtime.isHub(p.getServerWorld())&&world.getBlockState(hit.getBlockPos()).isOf(Content.STASH_TERMINAL)&&p.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(hit.getBlockPos()))<=25)
                    runtime.stashAction(p,new Protocol.StashAction(0,0,0,0));
                else if(runtime!=null && hand==Hand.MAIN_HAND)runtime.interact(p,hit.getBlockPos());
                return ActionResult.FAIL;
            }
            return restricted(p)?ActionResult.FAIL:ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player,world,hand)->{
            if(!world.isClient && player instanceof ServerPlayerEntity p && locked(p))return TypedActionResult.fail(player.getStackInHand(hand));
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
        AttackBlockCallback.EVENT.register((player,world,hand,pos,direction)->isolated(world)||(player instanceof ServerPlayerEntity p&&restricted(p))?ActionResult.FAIL:ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(dev.svextract.mission.NpcRoles.protectedService(entity))return ActionResult.FAIL;
            if(!world.isClient && player instanceof ServerPlayerEntity p && locked(p))return ActionResult.FAIL;
            if(isolated(world) && (world.getRegistryKey().equals(MissionRuntime.HAVEN)||entity instanceof ServerPlayerEntity))return ActionResult.FAIL;
            return ActionResult.PASS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->!dev.svextract.mission.NpcRoles.protectedService(entity)&&(runtime==null||runtime.allowActorDamage(entity,source))&&(!(entity instanceof ServerPlayerEntity p)||runtime==null||runtime.allowDamage(p,source,amount)));
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity,source,amount)->{
            if(dev.svextract.mission.NpcRoles.protectedService(entity))return false;
            if(runtime!=null&&!runtime.botDeath(entity,source))return false;
            if(entity instanceof ServerPlayerEntity p && runtime!=null && runtime.participating(p.getUuid())){
                runtime.death(p,source);return false;
            }
            return true;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(CommandManager.literal("svextract")
            .then(CommandManager.literal("haven").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.haven(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("skyrim").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.skyrim(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("deploy").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.deploy(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("lobby").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.openLobby(c.getSource().getPlayerOrThrow(),true);return 1;}))
            .then(CommandManager.literal("abort").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.abort(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("progress").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.progress(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("balances").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.balances(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("companiongun").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.equipCompanion(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("loot").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.loot(c.getSource().getPlayerOrThrow());return 1;}))
            .then(CommandManager.literal("stash").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.stashAction(c.getSource().getPlayerOrThrow(),new Protocol.StashAction(0,0,0,0));return 1;}))
            .then(CommandManager.literal("squad")
                .executes(c->{if(runtime==null)return disabled(c.getSource());runtime.squadStatus(c.getSource().getPlayerOrThrow());return 1;})
                .then(CommandManager.literal("invite").then(CommandManager.argument("player",net.minecraft.command.argument.EntityArgumentType.player()).executes(c->{if(runtime==null)return disabled(c.getSource());runtime.invite(c.getSource().getPlayerOrThrow(),net.minecraft.command.argument.EntityArgumentType.getPlayer(c,"player"));return 1;})))
                .then(CommandManager.literal("accept").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.acceptInvite(c.getSource().getPlayerOrThrow());return 1;}))
                .then(CommandManager.literal("leave").executes(c->{if(runtime==null)return disabled(c.getSource());runtime.leaveSquad(c.getSource().getPlayerOrThrow());return 1;})))
            .then(CommandManager.literal("diagnostics").requires(s->s.hasPermissionLevel(2)).executes(c->{
                if(runtime==null)return disabled(c.getSource());
                c.getSource().sendFeedback(()->Text.literal(new com.google.gson.Gson().toJson(runtime.report())),false);return 1;
            }))
            .then(CommandManager.literal("export").requires(s->s.hasPermissionLevel(2)).executes(c->{
                if(runtime==null)return disabled(c.getSource());
                try{String path=runtime.export().toString();c.getSource().sendFeedback(()->Text.literal("Performance report: "+path),false);return 1;}
                catch(Exception e){c.getSource().sendError(Text.literal(e.getMessage()));return 0;}
            }))
            .then(CommandManager.literal("reload").requires(s->s.hasPermissionLevel(2)).executes(c->{
                if(runtime==null)return disabled(c.getSource());
                try{runtime.reload(ServerConfig.load(configPath()));c.getSource().sendFeedback(()->Text.literal("SVEXTRACT configuration validated. New admissions use updated mission settings."),false);return 1;}
                catch(Exception e){c.getSource().sendError(Text.literal("Reload rejected; current configuration retained: "+e.getMessage()));return 0;}
            }))));
    }
    private static int disabled(net.minecraft.server.command.ServerCommandSource source){source.sendError(Text.literal("SVEXTRACT unavailable. Inspect the server initialization log."));return 0;}
}
