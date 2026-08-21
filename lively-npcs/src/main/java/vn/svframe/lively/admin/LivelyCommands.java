package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Practical command surface for inspection, semantic structures and world events. */
public final class LivelyCommands {
    private static final ConcurrentHashMap<UUID, BlockPos> POS1 = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, BlockPos> POS2 = new ConcurrentHashMap<>();

    private LivelyCommands() {}

    public static void install() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("lively")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(literal("debug")
                                .then(literal("performance").executes(ctx -> performance(ctx.getSource())))
                                .then(literal("events").executes(ctx -> listEvents(ctx.getSource()))))
                        .then(literal("pos1").executes(ctx -> setPos(ctx.getSource(), true)))
                        .then(literal("pos2").executes(ctx -> setPos(ctx.getSource(), false)))
                        .then(literal("structure")
                                .then(literal("list").executes(ctx -> listStructures(ctx.getSource())))
                                .then(literal("info").then(argument("id", StringArgumentType.word()).executes(ctx -> structureInfo(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                                .then(literal("create")
                                        .then(argument("id", StringArgumentType.word())
                                                .then(argument("type", StringArgumentType.word())
                                                        .executes(ctx -> createStructure(ctx.getSource(), StringArgumentType.getString(ctx,"id"), StringArgumentType.getString(ctx,"type")))))))
                        .then(literal("world")
                                .then(literal("event")
                                        .then(literal("list").executes(ctx -> listEvents(ctx.getSource())))
                                        .then(literal("stop").then(argument("id", StringArgumentType.word()).executes(ctx -> stopEvent(ctx.getSource(), StringArgumentType.getString(ctx,"id")))))
                                        .then(literal("start")
                                                .then(argument("category", StringArgumentType.word())
                                                        .then(argument("seed", StringArgumentType.word())
                                                                .then(argument("minutes", IntegerArgumentType.integer(1, 43200))
                                                                        .then(argument("intensity", DoubleArgumentType.doubleArg(0D,1D))
                                                                                .executes(ctx -> startEvent(ctx.getSource(), StringArgumentType.getString(ctx,"category"), StringArgumentType.getString(ctx,"seed"), IntegerArgumentType.getInteger(ctx,"minutes"), DoubleArgumentType.getDouble(ctx,"intensity")))))))))
        ));
    }

    private static int status(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Lively: " + LivelyApi.admin().status()), false); return 1;
    }
    private static int performance(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Lively performance: " + LivelyApi.profiler().snapshot()), false); return 1;
    }
    private static int setPos(ServerCommandSource source, boolean first) {
        if (source.getPlayer() == null) { source.sendError(Text.literal("Player only")); return 0; }
        BlockPos pos = source.getPlayer().getBlockPos();
        (first ? POS1 : POS2).put(source.getPlayer().getUuid(), pos);
        source.sendFeedback(() -> Text.literal((first ? "pos1" : "pos2") + " = " + pos.toShortString()), false); return 1;
    }
    private static int createStructure(ServerCommandSource source, String id, String type) {
        if (source.getPlayer() == null) { source.sendError(Text.literal("Player only")); return 0; }
        UUID player = source.getPlayer().getUuid(); BlockPos a=POS1.get(player), b=POS2.get(player);
        if (a==null||b==null) { source.sendError(Text.literal("Set /lively pos1 and /lively pos2 first")); return 0; }
        String world=source.getWorld().getRegistryKey().getValue().toString();
        var bounds=new SemanticStructureRegistry.Bounds(world,Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
        try { LivelyApi.admin().createStructure(id,type,bounds,Set.of(),Map.of(),null,null); source.sendFeedback(()->Text.literal("Created structure "+id+" ("+type+")"),false); return 1; }
        catch(RuntimeException ex){source.sendError(Text.literal("Cannot create structure: "+ex.getMessage()));return 0;}
    }
    private static int listStructures(ServerCommandSource source){var values=LivelyApi.structures().snapshot().structures().values();source.sendFeedback(()->Text.literal("Structures ("+values.size()+"): "+values.stream().map(SemanticStructureRegistry.Structure::id).sorted().toList()),false);return 1;}
    private static int structureInfo(ServerCommandSource source,String id){var structure=LivelyApi.structures().get(id);if(structure.isEmpty()){source.sendError(Text.literal("Unknown structure: "+id));return 0;}source.sendFeedback(()->Text.literal(structure.get().toString()),false);return 1;}
    private static int listEvents(ServerCommandSource source){var events=LivelyApi.events().activeEvents();source.sendFeedback(()->Text.literal("Active events ("+events.size()+"): "+events.stream().map(e->e.id()+":"+e.seed()).toList()),false);return 1;}
    private static int stopEvent(ServerCommandSource source,String id){try{var cancelled=LivelyApi.events().cancel(UUID.fromString(id));if(cancelled.isEmpty()){source.sendError(Text.literal("Unknown active event"));return 0;}source.sendFeedback(()->Text.literal("Cancelled event "+id),false);return 1;}catch(IllegalArgumentException ex){source.sendError(Text.literal("Invalid event UUID"));return 0;}}
    private static int startEvent(ServerCommandSource source,String category,String seed,int minutes,double intensity){try{WorldEventEngine.Category value=WorldEventEngine.Category.valueOf(category.toUpperCase(java.util.Locale.ROOT));var event=LivelyApi.admin().startEvent(value,seed,null,Set.of(),intensity,Duration.ofMinutes(minutes),Map.of("source","admin"));if(event.isEmpty()){source.sendError(Text.literal("Event rejected by policy"));return 0;}source.sendFeedback(()->Text.literal("Started event "+event.get().id()+" seed="+seed),false);return 1;}catch(IllegalArgumentException ex){source.sendError(Text.literal("Unknown category: "+category));return 0;}}
}
