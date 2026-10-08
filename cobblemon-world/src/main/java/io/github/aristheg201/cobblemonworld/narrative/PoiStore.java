package io.github.aristheg201.cobblemonworld.narrative;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;

/** World-authored interaction points; no coordinates are embedded in narrative copy. */
public final class PoiStore {
    public record Position(String dimension, int x, int y, int z) {
        public BlockPos block(){return new BlockPos(x,y,z);}
        public boolean near(ServerPlayer p, double radius){return dimension.equals(p.level().dimension().location().toString()) && p.distanceToSqr(x+.5,y+.5,z+.5)<=radius*radius;}
    }
    public static final Map<String,Position> PLACES = new LinkedHashMap<>();
    private static Path file;
    public static void load(MinecraftServer server) {
        file=server.getWorldPath(LevelResource.ROOT).resolve("cobblemonworld/narrative_points.json");PLACES.clear();
        if(Files.exists(file))try {var root=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();for(var e:root.entrySet())PLACES.put(e.getKey(),new Gson().fromJson(e.getValue(),Position.class));}
        catch(Exception e){throw new IllegalStateException("Cannot load authored narrative points; refusing to overwrite",e);}
    }
    public static void save(){try {if(file!=null){Files.createDirectories(file.getParent());var temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,new Gson().toJson(PLACES));Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}
        catch(Exception e){CobblemonWorldMod.LOGGER.error("Cannot save narrative points",e);}}
    public static void register(){
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(Commands.literal("cworldpoi").requires(s->s.hasPermission(2))
            .then(Commands.literal("place").then(Commands.argument("id",StringArgumentType.word()).suggests((ctx,b)->{NarrativeRegistry.INSTANCE.pois.keySet().forEach(b::suggest);return b.buildFuture();})
            .executes(ctx->{ServerPlayer p=ctx.getSource().getPlayerOrException();String id=StringArgumentType.getString(ctx,"id");if(!NarrativeRegistry.INSTANCE.pois.containsKey(id))return 0;
                BlockPos pos=p.blockPosition().below();PLACES.put(id,new Position(p.level().dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ()));save();return 1;})))));
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(!(player instanceof ServerPlayer p))return InteractionResult.PASS;
            for(var e:PLACES.entrySet())if(e.getValue().dimension().equals(world.dimension().location().toString()) && e.getValue().block().equals(hit.getBlockPos())){
                var definition=NarrativeRegistry.INSTANCE.pois.get(e.getKey());
                if(definition!=null && (definition.block()==null || definition.block().isBlank() || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(world.getBlockState(hit.getBlockPos()).getBlock()).toString().equals(definition.block()))){
                    return ConversationService.openPoint(p,e.getKey())?InteractionResult.SUCCESS:InteractionResult.PASS;
                }
            }return InteractionResult.PASS;
        });
    }
}
