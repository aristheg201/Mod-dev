package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class LivelyCommands {
    private static final ConcurrentHashMap<UUID, BlockPos> POS1 = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, BlockPos> POS2 = new ConcurrentHashMap<>();
    private LivelyCommands() {}

    public static void install() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> root = literal("lively").requires(source -> source.hasPermissionLevel(2))
                    .then(literal("status").executes(ctx -> status(ctx.getSource())))
                    .then(literal("pos1").executes(ctx -> setPos(ctx.getSource(), true)))
                    .then(literal("pos2").executes(ctx -> setPos(ctx.getSource(), false)));
            root.then(npcCommands()); root.then(debugCommands()); root.then(structureCommands()); root.then(worldCommands());
            dispatcher.register(root);
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> npcCommands() {
        LiteralArgumentBuilder<ServerCommandSource> npc = literal("npc")
                .then(literal("list").executes(ctx -> npcList(ctx.getSource())))
                .then(literal("info").then(argument("id", StringArgumentType.word()).executes(ctx -> npcInfo(ctx.getSource(), id(ctx,"id")))))
                .then(literal("spawn").then(argument("id", StringArgumentType.word()).executes(ctx -> flag(ctx.getSource(), LivelyApi.npcs().spawn(ctx.getSource().getServer(), id(ctx,"id")), "spawned"))))
                .then(literal("despawn").then(argument("id", StringArgumentType.word()).executes(ctx -> flag(ctx.getSource(), LivelyApi.npcs().despawn(ctx.getSource().getServer(), id(ctx,"id")), "despawned"))))
                .then(literal("remove").then(argument("id", StringArgumentType.word()).executes(ctx -> flag(ctx.getSource(), LivelyApi.npcs().remove(ctx.getSource().getServer(), id(ctx,"id")), "removed"))))
                .then(literal("tp").then(argument("id", StringArgumentType.word()).executes(ctx -> npcTeleport(ctx.getSource(), id(ctx,"id")))))
                .then(literal("look").then(argument("id", StringArgumentType.word()).executes(ctx -> npcLook(ctx.getSource(), id(ctx,"id")))));

        LiteralArgumentBuilder<ServerCommandSource> create = literal("create");
        create.then(literal("player")
                .then(argument("name", StringArgumentType.word())
                        .then(argument("role", StringArgumentType.word())
                                .executes(ctx -> createNpc(ctx.getSource(), NpcDefinition.BodyType.PLAYER, "", StringArgumentType.getString(ctx,"name"), StringArgumentType.getString(ctx,"role"), ""))
                                .then(argument("skin", StringArgumentType.word())
                                        .executes(ctx -> createNpc(ctx.getSource(), NpcDefinition.BodyType.PLAYER, "", StringArgumentType.getString(ctx,"name"), StringArgumentType.getString(ctx,"role"), StringArgumentType.getString(ctx,"skin")))))));
        create.then(literal("vanilla")
                .then(argument("entity", StringArgumentType.word())
                        .then(argument("name", StringArgumentType.word())
                                .then(argument("role", StringArgumentType.word())
                                        .executes(ctx -> createNpc(ctx.getSource(), NpcDefinition.BodyType.VANILLA, StringArgumentType.getString(ctx,"entity"), StringArgumentType.getString(ctx,"name"), StringArgumentType.getString(ctx,"role"), ""))))));
        create.then(literal("external")
                .then(argument("body", StringArgumentType.word())
                        .then(argument("name", StringArgumentType.word())
                                .then(argument("role", StringArgumentType.word())
                                        .executes(ctx -> createNpc(ctx.getSource(), NpcDefinition.BodyType.EXTERNAL, StringArgumentType.getString(ctx,"body"), StringArgumentType.getString(ctx,"name"), StringArgumentType.getString(ctx,"role"), ""))))));
        npc.then(create);
        return npc;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> debugCommands() {
        return literal("debug").then(literal("performance").executes(ctx -> performance(ctx.getSource())))
                .then(literal("events").executes(ctx -> listEvents(ctx.getSource())));
    }
    private static LiteralArgumentBuilder<ServerCommandSource> structureCommands() {
        LiteralArgumentBuilder<ServerCommandSource> structure = literal("structure")
                .then(literal("list").executes(ctx -> listStructures(ctx.getSource())))
                .then(literal("info").then(argument("id", StringArgumentType.word()).executes(ctx -> structureInfo(ctx.getSource(), StringArgumentType.getString(ctx,"id")))));
        structure.then(literal("create").then(argument("id", StringArgumentType.word()).then(argument("type", StringArgumentType.word())
                .executes(ctx -> createStructure(ctx.getSource(), StringArgumentType.getString(ctx,"id"), StringArgumentType.getString(ctx,"type"))))));
        return structure;
    }
    private static LiteralArgumentBuilder<ServerCommandSource> worldCommands() {
        LiteralArgumentBuilder<ServerCommandSource> event = literal("event")
                .then(literal("list").executes(ctx -> listEvents(ctx.getSource())))
                .then(literal("stop").then(argument("id", StringArgumentType.word()).executes(ctx -> stopEvent(ctx.getSource(), StringArgumentType.getString(ctx,"id")))));
        event.then(literal("start").then(argument("category", StringArgumentType.word()).then(argument("seed", StringArgumentType.word())
                .then(argument("minutes", IntegerArgumentType.integer(1,43200)).then(argument("intensity", DoubleArgumentType.doubleArg(0D,1D))
                        .executes(ctx -> startEvent(ctx.getSource(), StringArgumentType.getString(ctx,"category"), StringArgumentType.getString(ctx,"seed"), IntegerArgumentType.getInteger(ctx,"minutes"), DoubleArgumentType.getDouble(ctx,"intensity"))))))));
        return literal("world").then(event);
    }

    private static int createNpc(ServerCommandSource source, NpcDefinition.BodyType type, String body, String name, String role, String skin) {
        if (source.getPlayer() == null) { source.sendError(Text.literal("Player only")); return 0; }
        String world = source.getWorld().getRegistryKey().getValue().toString(); Vec3d pos = source.getPosition();
        try {
            NpcDefinition definition = LivelyApi.npcs().create(name, role, type, body, skin, world, pos, source.getPlayer().getYaw(), source.getPlayer().getPitch());
            if (!LivelyApi.npcs().spawn(source.getServer(), definition.id())) { source.sendError(Text.literal("Created definition but body provider could not spawn it")); return 0; }
            source.sendFeedback(() -> Text.literal("Created Lively NPC " + definition.name() + " id=" + definition.id() + " body=" + type), false); return 1;
        } catch (RuntimeException ex) { source.sendError(Text.literal("NPC create failed: " + ex.getMessage())); return 0; }
    }
    private static int npcList(ServerCommandSource source) { var values=LivelyApi.npcs().snapshot().values(); source.sendFeedback(() -> Text.literal("NPCs ("+values.size()+"): "+values.stream().map(n->n.id()+":"+n.name()+":"+n.bodyType()).toList()),false); return 1; }
    private static int npcInfo(ServerCommandSource source, UUID id) { var npc=LivelyApi.npcs().get(id); if(npc.isEmpty()){source.sendError(Text.literal("Unknown NPC"));return 0;} source.sendFeedback(() -> Text.literal(npc.get().toString()),false); return 1; }
    private static int npcTeleport(ServerCommandSource source, UUID id) { String world=source.getWorld().getRegistryKey().getValue().toString(); return flag(source,LivelyApi.npcs().teleport(source.getServer(),id,world,source.getPosition(),source.getRotation().y,source.getRotation().x),"teleported"); }
    private static int npcLook(ServerCommandSource source, UUID id) { return flag(source,LivelyApi.npcs().lookAt(source.getServer(),id,source.getPosition()),"look target updated"); }
    private static int flag(ServerCommandSource source, boolean ok, String text) { if(!ok){source.sendError(Text.literal("Operation failed"));return 0;} source.sendFeedback(() -> Text.literal("Lively NPC "+text),false);return 1; }
    private static UUID id(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,String key){try{return UUID.fromString(StringArgumentType.getString(ctx,key));}catch(IllegalArgumentException ex){throw new IllegalArgumentException("invalid npc UUID");}}

    private static int status(ServerCommandSource source){source.sendFeedback(()->Text.literal("Lively: "+LivelyApi.admin().status()+", npcs="+(LivelyApi.npcs()==null?0:LivelyApi.npcs().snapshot().size())),false);return 1;}
    private static int performance(ServerCommandSource source){source.sendFeedback(()->Text.literal("Lively performance: "+LivelyApi.profiler().snapshot()),false);return 1;}
    private static int setPos(ServerCommandSource source,boolean first){if(source.getPlayer()==null){source.sendError(Text.literal("Player only"));return 0;}BlockPos pos=source.getPlayer().getBlockPos();(first?POS1:POS2).put(source.getPlayer().getUuid(),pos);source.sendFeedback(()->Text.literal((first?"pos1":"pos2")+" = "+pos.toShortString()),false);return 1;}
    private static int createStructure(ServerCommandSource source,String id,String type){if(source.getPlayer()==null){source.sendError(Text.literal("Player only"));return 0;}UUID player=source.getPlayer().getUuid();BlockPos a=POS1.get(player),b=POS2.get(player);if(a==null||b==null){source.sendError(Text.literal("Set pos1/pos2 first"));return 0;}String world=source.getWorld().getRegistryKey().getValue().toString();var bounds=new SemanticStructureRegistry.Bounds(world,Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));LivelyApi.admin().createStructure(id,type,bounds,Set.of(),Map.of(),null,null);source.sendFeedback(()->Text.literal("Created structure "+id),false);return 1;}
    private static int listStructures(ServerCommandSource source){var values=LivelyApi.structures().snapshot().structures().values();source.sendFeedback(()->Text.literal("Structures ("+values.size()+"): "+values.stream().map(SemanticStructureRegistry.Structure::id).sorted().toList()),false);return 1;}
    private static int structureInfo(ServerCommandSource source,String id){var s=LivelyApi.structures().get(id);if(s.isEmpty()){source.sendError(Text.literal("Unknown structure"));return 0;}source.sendFeedback(()->Text.literal(s.get().toString()),false);return 1;}
    private static int listEvents(ServerCommandSource source){var events=LivelyApi.events().activeEvents();source.sendFeedback(()->Text.literal("Active events ("+events.size()+"): "+events.stream().map(e->e.id()+":"+e.seed()).toList()),false);return 1;}
    private static int stopEvent(ServerCommandSource source,String id){try{return flag(source,LivelyApi.events().cancel(UUID.fromString(id)).isPresent(),"event cancelled");}catch(IllegalArgumentException ex){source.sendError(Text.literal("Invalid event UUID"));return 0;}}
    private static int startEvent(ServerCommandSource source,String category,String seed,int minutes,double intensity){try{WorldEventEngine.Category value=WorldEventEngine.Category.valueOf(category.toUpperCase(java.util.Locale.ROOT));var event=LivelyApi.admin().startEvent(value,seed,null,Set.of(),intensity,Duration.ofMinutes(minutes),Map.of("source","admin"));if(event.isEmpty()){source.sendError(Text.literal("Event rejected"));return 0;}source.sendFeedback(()->Text.literal("Started event "+event.get().id()),false);return 1;}catch(IllegalArgumentException ex){source.sendError(Text.literal("Unknown category"));return 0;}}
}
