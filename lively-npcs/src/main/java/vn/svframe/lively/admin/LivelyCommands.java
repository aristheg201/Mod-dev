package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.event.StoryArcEngine;
import vn.svframe.lively.event.StorySeedEngine;
import vn.svframe.lively.event.WorldEventEngine;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcRuntime;
import vn.svframe.lively.persistence.StructureTransferStore;
import vn.svframe.lively.quest.QuestCortex;
import vn.svframe.lively.quest.QuestRuntime;
import vn.svframe.lively.schedule.ScheduleEngine;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Admin and player command surface. Builders are deliberately split to avoid fragile nested Brigadier chains. */
public final class LivelyCommands {
    private static final StructureTransferStore STRUCTURE_IO = new StructureTransferStore(
            FabricLoader.getInstance().getConfigDir().resolve("livelynpcs").resolve("structures"));

    private LivelyCommands() {}

    public static void install() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> root = literal("lively");
            root.then(literal("status").requires(s -> permit(s, "lively.admin.status", 2)).executes(ctx -> status(ctx.getSource())));
            root.then(literal("pos1").requires(s -> permit(s, "lively.admin.structure", 2)).executes(ctx -> setPos(ctx.getSource(), true)));
            root.then(literal("pos2").requires(s -> permit(s, "lively.admin.structure", 2)).executes(ctx -> setPos(ctx.getSource(), false)));
            root.then(literal("wand").requires(s -> permit(s, "lively.admin.structure", 2)).executes(ctx -> giveWand(ctx.getSource())));
            root.then(npcCommands());
            root.then(questCommands());
            root.then(structureCommands());
            root.then(worldCommands());
            root.then(storyCommands());
            root.then(debugCommands());
            dispatcher.register(root);
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> npcCommands() {
        LiteralArgumentBuilder<ServerCommandSource> npc = literal("npc").requires(s -> permit(s, "lively.admin.npc", 2));
        npc.then(literal("list").executes(ctx -> npcList(ctx.getSource())));
        npc.then(literal("info").then(argument("id", StringArgumentType.word()).executes(ctx -> npcInfo(ctx.getSource(), id(ctx, "id")))));
        npc.then(literal("spawn").then(argument("id", StringArgumentType.word()).executes(ctx -> flag(ctx.getSource(), LivelyApi.npcs().spawn(ctx.getSource().getServer(), id(ctx, "id")), "spawned"))));
        npc.then(literal("despawn").then(argument("id", StringArgumentType.word()).executes(ctx -> flag(ctx.getSource(), LivelyApi.npcs().despawn(ctx.getSource().getServer(), id(ctx, "id")), "despawned"))));
        npc.then(literal("remove").then(argument("id", StringArgumentType.word()).executes(ctx -> flag(ctx.getSource(), LivelyApi.npcs().remove(ctx.getSource().getServer(), id(ctx, "id")), "removed"))));
        npc.then(literal("tp").then(argument("id", StringArgumentType.word()).executes(ctx -> npcTeleport(ctx.getSource(), id(ctx, "id")))));
        npc.then(literal("look").then(argument("id", StringArgumentType.word()).executes(ctx -> npcLook(ctx.getSource(), id(ctx, "id")))));
        npc.then(createCommands());
        npc.then(editCommands());
        npc.then(skinCommands());
        npc.then(navigationCommands());
        npc.then(scheduleCommands());
        return npc;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createCommands() {
        LiteralArgumentBuilder<ServerCommandSource> create = literal("create");

        var playerRole = argument("role", StringArgumentType.word())
                .executes(ctx -> createNpc(ctx.getSource(), NpcDefinition.BodyType.PLAYER, "", str(ctx, "name"), str(ctx, "role"), ""));
        playerRole.then(argument("skin", StringArgumentType.greedyString())
                .executes(ctx -> createNpc(ctx.getSource(), NpcDefinition.BodyType.PLAYER, "", str(ctx, "name"), str(ctx, "role"), str(ctx, "skin"))));
        create.then(literal("player").then(argument("name", StringArgumentType.word()).then(playerRole)));

        create.then(literal("vanilla")
                .then(argument("entity", StringArgumentType.word())
                        .then(argument("name", StringArgumentType.word())
                                .then(argument("role", StringArgumentType.word())
                                        .executes(ctx -> createNpc(ctx.getSource(), NpcDefinition.BodyType.VANILLA,
                                                str(ctx, "entity"), str(ctx, "name"), str(ctx, "role"), ""))))));

        create.then(literal("external")
                .then(argument("name", StringArgumentType.word())
                        .then(argument("role", StringArgumentType.word())
                                .then(argument("body", StringArgumentType.greedyString())
                                        .executes(ctx -> createNpc(ctx.getSource(), NpcDefinition.BodyType.EXTERNAL,
                                                str(ctx, "body"), str(ctx, "name"), str(ctx, "role"), ""))))));

        create.then(literal("pokemon")
                .then(argument("name", StringArgumentType.word())
                        .then(argument("role", StringArgumentType.word())
                                .then(argument("properties", StringArgumentType.greedyString())
                                        .executes(ctx -> createNpc(ctx.getSource(), NpcDefinition.BodyType.EXTERNAL,
                                                str(ctx, "properties"), str(ctx, "name"), str(ctx, "role"), ""))))));
        return create;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> editCommands() {
        LiteralArgumentBuilder<ServerCommandSource> set = literal("set");
        var npc = argument("id", StringArgumentType.word());
        npc.then(literal("name").then(argument("value", StringArgumentType.word())
                .executes(ctx -> rename(ctx.getSource(), id(ctx, "id"), str(ctx, "value"), null))));
        npc.then(literal("role").then(argument("value", StringArgumentType.word())
                .executes(ctx -> rename(ctx.getSource(), id(ctx, "id"), null, str(ctx, "value")))));
        npc.then(literal("flag").then(argument("flag", StringArgumentType.word())
                .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> setNpcFlag(ctx.getSource(), id(ctx, "id"), str(ctx, "flag"), BoolArgumentType.getBool(ctx, "value"))))));
        npc.then(literal("trait").then(argument("trait", StringArgumentType.word())
                .then(argument("value", DoubleArgumentType.doubleArg(0D, 1D))
                        .executes(ctx -> flag(ctx.getSource(), LivelyApi.npcs().setTrait(id(ctx, "id"), str(ctx, "trait"), DoubleArgumentType.getDouble(ctx, "value")), "trait updated")))));
        npc.then(literal("need").then(argument("need", StringArgumentType.word())
                .then(argument("value", DoubleArgumentType.doubleArg(0D, 1D))
                        .executes(ctx -> flag(ctx.getSource(), LivelyApi.npcs().setNeed(id(ctx, "id"), str(ctx, "need"), DoubleArgumentType.getDouble(ctx, "value")), "need updated")))));
        npc.then(literal("meta").then(argument("key", StringArgumentType.word())
                .then(argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> flag(ctx.getSource(), LivelyApi.npcs().setMetadata(id(ctx, "id"), str(ctx, "key"), str(ctx, "value")), "metadata updated")))));

        var body = literal("body");
        body.then(literal("player").executes(ctx -> changeBody(ctx.getSource(), id(ctx, "id"), NpcDefinition.BodyType.PLAYER, "", "")));
        body.then(literal("vanilla").then(argument("entity", StringArgumentType.word())
                .executes(ctx -> changeBody(ctx.getSource(), id(ctx, "id"), NpcDefinition.BodyType.VANILLA, str(ctx, "entity"), ""))));
        body.then(literal("external").then(argument("body", StringArgumentType.greedyString())
                .executes(ctx -> changeBody(ctx.getSource(), id(ctx, "id"), NpcDefinition.BodyType.EXTERNAL, str(ctx, "body"), ""))));
        npc.then(body);
        set.then(npc);
        return set;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> skinCommands() {
        LiteralArgumentBuilder<ServerCommandSource> skin = literal("skin");
        var npc = argument("id", StringArgumentType.word());
        npc.then(literal("default").executes(ctx -> setSkin(ctx.getSource(), id(ctx, "id"), "default")));
        npc.then(literal("mojang").then(argument("username", StringArgumentType.word())
                .executes(ctx -> setSkin(ctx.getSource(), id(ctx, "id"), "mojang:" + str(ctx, "username")))));
        npc.then(literal("url").then(argument("url", StringArgumentType.greedyString())
                .executes(ctx -> setSkin(ctx.getSource(), id(ctx, "id"), "url:" + str(ctx, "url")))));
        npc.then(literal("mineskin").then(argument("skinId", StringArgumentType.word())
                .executes(ctx -> setSkin(ctx.getSource(), id(ctx, "id"), "mineskin:" + str(ctx, "skinId")))));
        npc.then(literal("source").then(argument("source", StringArgumentType.greedyString())
                .executes(ctx -> setSkin(ctx.getSource(), id(ctx, "id"), str(ctx, "source")))));
        npc.then(literal("texture").then(argument("value", StringArgumentType.greedyString())
                .executes(ctx -> setSkin(ctx.getSource(), id(ctx, "id"), "texture:" + str(ctx, "value")))));
        skin.then(npc);
        return skin;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> navigationCommands() {
        LiteralArgumentBuilder<ServerCommandSource> nav = literal("nav");
        nav.then(literal("stop").then(argument("id", StringArgumentType.word())
                .executes(ctx -> flag(ctx.getSource(), LivelyApi.worldNavigation().stop(id(ctx, "id")), "navigation stopped"))));
        nav.then(literal("status").then(argument("id", StringArgumentType.word())
                .executes(ctx -> navStatus(ctx.getSource(), id(ctx, "id")))));
        nav.then(literal("here").then(argument("id", StringArgumentType.word())
                .executes(ctx -> navHere(ctx.getSource(), id(ctx, "id")))));
        nav.then(literal("structure").then(argument("id", StringArgumentType.word())
                .then(argument("structure", StringArgumentType.word())
                        .executes(ctx -> flag(ctx.getSource(), LivelyApi.worldNavigation().goToStructure(id(ctx, "id"), str(ctx, "structure")), "navigation started")))));
        nav.then(literal("goto").then(argument("id", StringArgumentType.word())
                .then(argument("x", DoubleArgumentType.doubleArg())
                        .then(argument("y", DoubleArgumentType.doubleArg())
                                .then(argument("z", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> navGoto(ctx.getSource(), id(ctx, "id"),
                                                DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z"))))))));
        nav.then(literal("follow").then(argument("id", StringArgumentType.word())
                .then(argument("target", StringArgumentType.word())
                        .executes(ctx -> navTrack(ctx.getSource(), id(ctx, "id"), str(ctx, "target"), false)))));
        nav.then(literal("escort").then(argument("id", StringArgumentType.word())
                .then(argument("target", StringArgumentType.word())
                        .executes(ctx -> navTrack(ctx.getSource(), id(ctx, "id"), str(ctx, "target"), true)))));
        nav.then(literal("flee").then(argument("id", StringArgumentType.word())
                .then(argument("distance", DoubleArgumentType.doubleArg(4D, 48D))
                        .executes(ctx -> flag(ctx.getSource(), LivelyApi.worldNavigation().flee(id(ctx, "id"),
                                ctx.getSource().getWorld().getRegistryKey().getValue().toString(), ctx.getSource().getPosition(),
                                DoubleArgumentType.getDouble(ctx, "distance")), "flee started")))));
        return nav;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> scheduleCommands() {
        LiteralArgumentBuilder<ServerCommandSource> schedule = literal("schedule");
        schedule.then(literal("clear").then(argument("id", StringArgumentType.word())
                .executes(ctx -> setSchedule(ctx.getSource(), id(ctx, "id"), List.of()))));

        var priority = argument("priority", IntegerArgumentType.integer(0, 100))
                .executes(ctx -> addSchedule(ctx.getSource(), id(ctx, "id"),
                        IntegerArgumentType.getInteger(ctx, "start"), IntegerArgumentType.getInteger(ctx, "end"),
                        str(ctx, "activity"), str(ctx, "location"), IntegerArgumentType.getInteger(ctx, "priority")));
        var location = argument("location", StringArgumentType.word()).then(priority);
        var activity = argument("activity", StringArgumentType.word()).then(location);
        var end = argument("end", IntegerArgumentType.integer(0, 1440)).then(activity);
        var start = argument("start", IntegerArgumentType.integer(0, 1439)).then(end);
        var id = argument("id", StringArgumentType.word()).then(start);
        schedule.then(literal("add").then(id));
        return schedule;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> questCommands() {
        LiteralArgumentBuilder<ServerCommandSource> q = literal("quest");
        q.then(literal("list").requires(s -> permit(s, "lively.quest.use", 0)).executes(ctx -> questList(ctx.getSource(), false)));
        q.then(literal("mine").requires(s -> permit(s, "lively.quest.use", 0)).executes(ctx -> questList(ctx.getSource(), true)));
        q.then(literal("info").requires(s -> permit(s, "lively.quest.use", 0)).then(argument("id", StringArgumentType.word()).executes(ctx -> questInfo(ctx.getSource(), str(ctx, "id")))));
        q.then(literal("claim").requires(s -> permit(s, "lively.quest.use", 0)).then(argument("id", StringArgumentType.word()).executes(ctx -> questClaim(ctx.getSource(), str(ctx, "id")))));
        q.then(literal("generate").requires(s -> permit(s, "lively.admin.quest", 2)).then(argument("npc", StringArgumentType.word()).executes(ctx -> questGenerate(ctx.getSource(), id(ctx, "npc")))));
        q.then(literal("cancel").requires(s -> permit(s, "lively.admin.quest", 2)).then(argument("id", StringArgumentType.word()).executes(ctx -> questCancel(ctx.getSource(), str(ctx, "id")))));
        return q;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> structureCommands() {
        LiteralArgumentBuilder<ServerCommandSource> s = literal("structure").requires(src -> permit(src, "lively.admin.structure", 2));
        s.then(literal("list").executes(ctx -> listStructures(ctx.getSource())));
        s.then(literal("info").then(argument("id", StringArgumentType.word()).executes(ctx -> structureInfo(ctx.getSource(), str(ctx, "id")))));
        s.then(literal("create").then(argument("id", StringArgumentType.word())
                .then(argument("type", StringArgumentType.word()).executes(ctx -> createStructure(ctx.getSource(), str(ctx, "id"), str(ctx, "type"))))));
        s.then(literal("remove").then(argument("id", StringArgumentType.word())
                .executes(ctx -> flag(ctx.getSource(), LivelyApi.structures().remove(str(ctx, "id")), "structure removed"))));

        LiteralArgumentBuilder<ServerCommandSource> point = literal("point");
        point.then(literal("set").then(argument("id", StringArgumentType.word())
                .then(argument("name", StringArgumentType.word()).executes(ctx -> structurePoint(ctx.getSource(), str(ctx, "id"), str(ctx, "name"))))));
        point.then(literal("remove").then(argument("id", StringArgumentType.word())
                .then(argument("name", StringArgumentType.word()).executes(ctx -> flag(ctx.getSource(),
                        LivelyApi.structures().removePoint(str(ctx, "id"), str(ctx, "name")).isPresent(), "structure point removed")))));
        s.then(point);

        s.then(literal("state").then(argument("id", StringArgumentType.word())
                .then(argument("state", StringArgumentType.word()).executes(ctx -> structureState(ctx.getSource(), str(ctx, "id"), str(ctx, "state"))))));
        s.then(literal("link").then(argument("id", StringArgumentType.word())
                .then(argument("parent", StringArgumentType.word())
                        .then(argument("town", StringArgumentType.word()).executes(ctx -> structureLink(ctx.getSource(), str(ctx, "id"), str(ctx, "parent"), str(ctx, "town")))))));
        s.then(literal("export").then(argument("id", StringArgumentType.word()).executes(ctx -> structureExport(ctx.getSource(), str(ctx, "id")))));
        s.then(literal("import").then(argument("file", StringArgumentType.word()).executes(ctx -> structureImport(ctx.getSource(), str(ctx, "file")))));
        return s;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> worldCommands() {
        LiteralArgumentBuilder<ServerCommandSource> world = literal("world").requires(s -> permit(s, "lively.admin.world", 2));
        LiteralArgumentBuilder<ServerCommandSource> event = literal("event");
        event.then(literal("list").executes(ctx -> listEvents(ctx.getSource())));
        event.then(literal("cancel").then(argument("id", StringArgumentType.word()).executes(ctx -> eventTransition(ctx.getSource(), str(ctx, "id"), "cancel"))));
        event.then(literal("finish").then(argument("id", StringArgumentType.word()).executes(ctx -> eventTransition(ctx.getSource(), str(ctx, "id"), "finish"))));
        event.then(literal("pause").then(argument("id", StringArgumentType.word()).executes(ctx -> eventTransition(ctx.getSource(), str(ctx, "id"), "pause"))));
        event.then(literal("resume").then(argument("id", StringArgumentType.word()).executes(ctx -> eventTransition(ctx.getSource(), str(ctx, "id"), "resume"))));

        var intensity = argument("intensity", DoubleArgumentType.doubleArg(0D, 1D))
                .executes(ctx -> startEvent(ctx.getSource(), str(ctx, "category"), str(ctx, "seed"),
                        IntegerArgumentType.getInteger(ctx, "minutes"), DoubleArgumentType.getDouble(ctx, "intensity")));
        var minutes = argument("minutes", IntegerArgumentType.integer(1, 43200)).then(intensity);
        var seed = argument("seed", StringArgumentType.word()).then(minutes);
        var category = argument("category", StringArgumentType.word()).then(seed);
        event.then(literal("start").then(category));
        world.then(event);
        return world;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> storyCommands() {
        LiteralArgumentBuilder<ServerCommandSource> story = literal("story").requires(s -> permit(s, "lively.admin.story", 2));
        story.then(literal("arc").then(literal("list").executes(ctx -> storyArcList(ctx.getSource()))));
        story.then(literal("arc").then(literal("start")
                .then(argument("seed", StringArgumentType.word())
                        .then(argument("phases", IntegerArgumentType.integer(1, 32))
                                .then(argument("title", StringArgumentType.greedyString())
                                        .executes(ctx -> storyArcStart(ctx.getSource(), str(ctx, "seed"), IntegerArgumentType.getInteger(ctx, "phases"), str(ctx, "title"))))))));
        story.then(literal("arc").then(literal("state")
                .then(argument("id", StringArgumentType.word())
                        .then(argument("state", StringArgumentType.word())
                                .executes(ctx -> storyArcState(ctx.getSource(), str(ctx, "id"), str(ctx, "state")))))));
        story.then(literal("seed").then(literal("list").executes(ctx -> storySeedList(ctx.getSource()))));
        story.then(literal("seed").then(literal("remove").then(argument("id", StringArgumentType.word())
                .executes(ctx -> storySeedRemove(ctx.getSource(), str(ctx, "id"))))));
        story.then(literal("seed").then(literal("set")
                .then(argument("id", StringArgumentType.word())
                        .then(argument("category", StringArgumentType.word())
                                .then(argument("weight", DoubleArgumentType.doubleArg(0D, 1D))
                                        .then(argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> storySeedSet(ctx.getSource(), str(ctx, "id"), str(ctx, "category"),
                                                        DoubleArgumentType.getDouble(ctx, "weight"), BoolArgumentType.getBool(ctx, "enabled")))))))));
        return story;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> debugCommands() {
        LiteralArgumentBuilder<ServerCommandSource> debug = literal("debug").requires(s -> permit(s, "lively.admin.debug", 2));
        debug.then(literal("performance").executes(ctx -> performance(ctx.getSource())));
        debug.then(literal("events").executes(ctx -> listEvents(ctx.getSource())));
        debug.then(literal("integrations").executes(ctx -> integrations(ctx.getSource())));
        return debug;
    }

    private static int createNpc(ServerCommandSource source, NpcDefinition.BodyType type, String body, String name, String role, String skin) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return sourceError(source, "Player only");
        try {
            NpcDefinition definition = LivelyApi.npcs().create(name, role, type, body, skin,
                    source.getWorld().getRegistryKey().getValue().toString(), source.getPosition(), player.getYaw(), player.getPitch());
            if (!LivelyApi.npcs().spawn(source.getServer(), definition.id())) return sourceError(source, "Created definition but body provider could not spawn it");
            source.sendFeedback(() -> Text.literal("Created Lively NPC " + definition.name() + " id=" + definition.id() + " body=" + type), false);
            return 1;
        } catch (RuntimeException ex) {
            return sourceError(source, "NPC create failed: " + ex.getMessage());
        }
    }

    private static int setSkin(ServerCommandSource source, UUID id, String value) {
        try { return flag(source, LivelyApi.npcs().setSkin(source.getServer(), id, value), "skin source updated"); }
        catch (RuntimeException ex) { return sourceError(source, "Skin update failed: " + ex.getMessage()); }
    }

    private static int changeBody(ServerCommandSource source, UUID id, NpcDefinition.BodyType type, String key, String skin) {
        return flag(source, LivelyApi.npcs().changeBody(source.getServer(), id, type, key, skin), "body updated");
    }

    private static int rename(ServerCommandSource source, UUID id, String name, String role) {
        NpcDefinition old = LivelyApi.npcs().get(id).orElse(null);
        if (old == null) return sourceError(source, "Unknown NPC");
        return flag(source, LivelyApi.npcs().rename(id, name == null ? old.name() : name, role == null ? old.role() : role), "identity updated");
    }

    private static int setNpcFlag(ServerCommandSource source, UUID id, String raw, boolean value) {
        try { return flag(source, LivelyApi.npcs().setFlag(id, NpcRuntime.Flag.valueOf(raw.toUpperCase(Locale.ROOT)), value), "flag updated"); }
        catch (IllegalArgumentException ex) { return sourceError(source, "Unknown flag. Use ai, invulnerable, gravity, silent, name_visible"); }
    }

    private static int npcList(ServerCommandSource source) {
        var values = LivelyApi.npcs().snapshot().values();
        source.sendFeedback(() -> Text.literal("NPCs (" + values.size() + "): " + values.stream().map(n -> n.id() + ":" + n.name() + ":" + n.bodyType()).toList()), false);
        return 1;
    }

    private static int npcInfo(ServerCommandSource source, UUID id) {
        var npc = LivelyApi.npcs().get(id);
        if (npc.isEmpty()) return sourceError(source, "Unknown NPC");
        source.sendFeedback(() -> Text.literal(npc.get().toString()), false);
        return 1;
    }

    private static int npcTeleport(ServerCommandSource source, UUID id) {
        return flag(source, LivelyApi.npcs().teleport(source.getServer(), id,
                source.getWorld().getRegistryKey().getValue().toString(), source.getPosition(), source.getRotation().y, source.getRotation().x), "teleported");
    }

    private static int npcLook(ServerCommandSource source, UUID id) {
        return flag(source, LivelyApi.npcs().lookAt(source.getServer(), id, source.getPosition()), "look target updated");
    }

    private static int navHere(ServerCommandSource source, UUID id) {
        return flag(source, LivelyApi.worldNavigation().goTo(id, source.getWorld().getRegistryKey().getValue().toString(), source.getPosition()), "navigation started");
    }

    private static int navGoto(ServerCommandSource source, UUID id, double x, double y, double z) {
        return flag(source, LivelyApi.worldNavigation().goTo(id, source.getWorld().getRegistryKey().getValue().toString(), new Vec3d(x, y, z)), "navigation started");
    }

    private static int navTrack(ServerCommandSource source, UUID id, String target, boolean escort) {
        UUID targetId;
        try { targetId = UUID.fromString(target); }
        catch (IllegalArgumentException ignored) {
            ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(target);
            if (player == null) return sourceError(source, "Target player/NPC UUID not found");
            targetId = player.getUuid();
        }
        return flag(source, escort ? LivelyApi.worldNavigation().escort(id, targetId) : LivelyApi.worldNavigation().follow(id, targetId), escort ? "escort started" : "follow started");
    }

    private static int navStatus(ServerCommandSource source, UUID id) {
        var status = LivelyApi.worldNavigation().status(id);
        if (status.isEmpty()) return sourceError(source, "NPC has no active navigation");
        source.sendFeedback(() -> Text.literal(status.get().toString()), false);
        return 1;
    }

    private static int setSchedule(ServerCommandSource source, UUID id, List<ScheduleEngine.ScheduleEntry> entries) {
        if (LivelyApi.npcs().get(id).isEmpty()) return sourceError(source, "Unknown NPC");
        LivelyApi.schedules().setSchedule(new ActorId(id, ActorId.Kind.NPC), entries);
        source.sendFeedback(() -> Text.literal("Schedule updated"), false);
        return 1;
    }

    private static int addSchedule(ServerCommandSource source, UUID id, int start, int end, String activity, String location, int priority) {
        if (start == end) return sourceError(source, "start and end must differ");
        ActorId actor = new ActorId(id, ActorId.Kind.NPC);
        List<ScheduleEngine.ScheduleEntry> old = LivelyApi.schedules().snapshot().schedules().getOrDefault(actor, List.of());
        ArrayList<ScheduleEngine.ScheduleEntry> next = new ArrayList<>(old);
        next.add(new ScheduleEngine.ScheduleEntry(start, end, activity, location, priority, true, Map.of()));
        return setSchedule(source, id, next);
    }

    private static int questList(ServerCommandSource source, boolean mine) {
        ServerPlayerEntity player = source.getPlayer();
        List<QuestRuntime.Quest> values;
        if (mine) {
            if (player == null) return sourceError(source, "Player only");
            values = LivelyApi.quests().byOwner(new ActorId(player.getUuid(), ActorId.Kind.PLAYER));
        } else values = LivelyApi.quests().publicOffers();
        source.sendFeedback(() -> Text.literal((mine ? "My quests" : "Public quests") + " (" + values.size() + "): " + values.stream().map(q -> q.id() + ":" + q.title() + ":" + q.status()).toList()), false);
        return 1;
    }

    private static int questInfo(ServerCommandSource source, String raw) {
        try {
            QuestRuntime.Quest quest = LivelyApi.quests().snapshot().quests().get(UUID.fromString(raw));
            if (quest == null) return sourceError(source, "Unknown quest");
            source.sendFeedback(() -> Text.literal(quest.toString()), false);
            return 1;
        } catch (IllegalArgumentException ex) { return sourceError(source, "Invalid quest UUID"); }
    }

    private static int questClaim(ServerCommandSource source, String raw) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return sourceError(source, "Player only");
        try {
            UUID questId = UUID.fromString(raw);
            ActorId actor = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
            LivelyApi.actors().upsert(actor, player.getName().getString(), Map.of(),
                    Map.of("world", player.getServerWorld().getRegistryKey().getValue().toString()), Set.of("player"));
            return flag(source, LivelyApi.quests().claim(questId, actor).isPresent(), "quest claimed");
        } catch (IllegalArgumentException ex) { return sourceError(source, "Invalid quest UUID"); }
    }

    private static int questGenerate(ServerCommandSource source, UUID npcId) {
        var state = LivelyApi.states().snapshot(npcId);
        if (state.isEmpty()) return sourceError(source, "Unknown NPC state");
        QuestCortex cortex = new QuestCortex();
        var proposal = cortex.propose(state.get());
        if (proposal.isEmpty()) return sourceError(source, "NPC has no causal quest proposal right now");
        var p = proposal.get();
        if (!cortex.validate(p, state.get().revision()).allowed()) return sourceError(source, "Quest proposal failed validation");
        QuestRuntime.ObjectiveType type = p.type().contains("locate") ? QuestRuntime.ObjectiveType.EXPLORATION : QuestRuntime.ObjectiveType.COLLECTION;
        QuestRuntime.Quest quest = LivelyApi.quests().create(new ActorId(npcId, ActorId.Kind.NPC), null, "Việc từ " + state.get().name(),
                List.of(new QuestRuntime.Objective("main", type, p.target(), p.amount(), false, false, Map.of())), Duration.ofHours(6),
                Map.of("reward_budget", Long.toString(p.rewardBudget()), "generated", "npc"));
        source.sendFeedback(() -> Text.literal("Generated public quest " + quest.id()), false);
        return 1;
    }

    private static int questCancel(ServerCommandSource source, String raw) {
        try { return flag(source, LivelyApi.quests().cancel(UUID.fromString(raw)).isPresent(), "quest cancelled"); }
        catch (IllegalArgumentException ex) { return sourceError(source, "Invalid quest UUID"); }
    }

    private static int status(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Lively: " + LivelyApi.admin().status() + ", npcs=" +
                (LivelyApi.npcs() == null ? 0 : LivelyApi.npcs().snapshot().size()) + ", nav=" +
                (LivelyApi.worldNavigation() == null ? 0 : LivelyApi.worldNavigation().activeCount())), false);
        return 1;
    }

    private static int performance(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Lively performance: " + LivelyApi.profiler().snapshot()), false);
        return 1;
    }

    private static int integrations(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Integrations: economy=" + LivelyApi.externalEconomy().available() +
                ", holograms=" + LivelyApi.holograms().available() + ", waypoints=" + LivelyApi.waypoints().available() +
                ", claims=" + LivelyApi.claims().available()), false);
        return 1;
    }

    private static int setPos(ServerCommandSource source, boolean first) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return sourceError(source, "Player only");
        BlockPos pos = player.getBlockPos();
        SelectionService.set(player.getUuid(), first, source.getWorld().getRegistryKey().getValue().toString(), pos);
        source.sendFeedback(() -> Text.literal((first ? "pos1" : "pos2") + " = " + pos.toShortString()), false);
        return 1;
    }

    private static int giveWand(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return sourceError(source, "Player only");
        player.giveItemStack(SelectionWand.create());
        source.sendFeedback(() -> Text.literal("Lively selection wand added to inventory"), false);
        return 1;
    }

    private static int createStructure(ServerCommandSource source, String id, String type) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return sourceError(source, "Player only");
        SelectionService.Point a = SelectionService.first(player.getUuid()).orElse(null);
        SelectionService.Point b = SelectionService.second(player.getUuid()).orElse(null);
        if (a == null || b == null) return sourceError(source, "Set pos1/pos2 first");
        if (!a.world().equals(b.world())) return sourceError(source, "pos1 and pos2 must be in the same world");
        BlockPos ap = a.pos();
        BlockPos bp = b.pos();
        SemanticStructureRegistry.Bounds bounds = new SemanticStructureRegistry.Bounds(a.world(),
                Math.min(ap.getX(), bp.getX()), Math.min(ap.getY(), bp.getY()), Math.min(ap.getZ(), bp.getZ()),
                Math.max(ap.getX(), bp.getX()), Math.max(ap.getY(), bp.getY()), Math.max(ap.getZ(), bp.getZ()));
        LivelyApi.admin().createStructure(id, type, bounds, Set.of(),
                Map.of("entrance", source.getPosition().x + "," + source.getPosition().y + "," + source.getPosition().z), null, null);
        source.sendFeedback(() -> Text.literal("Created structure " + id), false);
        return 1;
    }

    private static int structurePoint(ServerCommandSource source, String id, String name) {
        return flag(source, LivelyApi.structures().setPoint(id, name, source.getPosition().x, source.getPosition().y, source.getPosition().z).isPresent(), "structure point updated");
    }

    private static int structureState(ServerCommandSource source, String id, String value) {
        try { return flag(source, LivelyApi.structures().setState(id, SemanticStructureRegistry.OperationalState.valueOf(value.toUpperCase(Locale.ROOT))).isPresent(), "structure state updated"); }
        catch (IllegalArgumentException ex) { return sourceError(source, "Unknown structure state"); }
    }

    private static int structureLink(ServerCommandSource source, String id, String parent, String town) {
        String p = parent.equalsIgnoreCase("none") ? null : parent;
        String t = town.equalsIgnoreCase("none") ? null : town;
        return flag(source, LivelyApi.structures().setMembership(id, p, t).isPresent(), "structure link updated");
    }

    private static int structureExport(ServerCommandSource source, String id) {
        var structure = LivelyApi.structures().get(id);
        if (structure.isEmpty()) return sourceError(source, "Unknown structure");
        try {
            var path = STRUCTURE_IO.exportStructure(structure.get());
            source.sendFeedback(() -> Text.literal("Exported structure to " + path), false);
            return 1;
        } catch (RuntimeException ex) { return sourceError(source, ex.getMessage()); }
    }

    private static int structureImport(ServerCommandSource source, String file) {
        try {
            var structure = STRUCTURE_IO.importStructure(file);
            if (structure.isEmpty()) return sourceError(source, "Structure file not found");
            LivelyApi.structures().register(structure.get());
            source.sendFeedback(() -> Text.literal("Imported structure " + structure.get().id()), false);
            return 1;
        } catch (RuntimeException ex) { return sourceError(source, ex.getMessage()); }
    }

    private static int listStructures(ServerCommandSource source) {
        var values = LivelyApi.structures().snapshot().structures().values();
        source.sendFeedback(() -> Text.literal("Structures (" + values.size() + "): " + values.stream().map(SemanticStructureRegistry.Structure::id).sorted().toList()), false);
        return 1;
    }

    private static int structureInfo(ServerCommandSource source, String id) {
        var structure = LivelyApi.structures().get(id);
        if (structure.isEmpty()) return sourceError(source, "Unknown structure");
        source.sendFeedback(() -> Text.literal(structure.get().toString()), false);
        return 1;
    }

    private static int listEvents(ServerCommandSource source) {
        var events = LivelyApi.events().activeEvents();
        source.sendFeedback(() -> Text.literal("Active events (" + events.size() + "): " + events.stream().map(e -> e.id() + ":" + e.seed() + ":" + e.phase()).toList()), false);
        return 1;
    }

    private static int eventTransition(ServerCommandSource source, String raw, String transition) {
        try {
            UUID id = UUID.fromString(raw);
            boolean ok = switch (transition) {
                case "pause" -> LivelyApi.events().pause(id).isPresent();
                case "resume" -> LivelyApi.events().resume(id).isPresent();
                case "finish" -> LivelyApi.events().finish(id).isPresent();
                default -> LivelyApi.events().cancel(id).isPresent();
            };
            return flag(source, ok, "event " + transition + "d");
        } catch (IllegalArgumentException ex) { return sourceError(source, "Invalid event UUID"); }
    }

    private static int startEvent(ServerCommandSource source, String category, String seed, int minutes, double intensity) {
        try {
            WorldEventEngine.Category value = WorldEventEngine.Category.valueOf(category.toUpperCase(Locale.ROOT));
            var event = LivelyApi.admin().startEvent(value, seed, null, Set.of(), intensity, Duration.ofMinutes(minutes), Map.of("source", "admin"));
            if (event.isEmpty()) return sourceError(source, "Event rejected");
            source.sendFeedback(() -> Text.literal("Started event " + event.get().id()), false);
            return 1;
        } catch (IllegalArgumentException ex) { return sourceError(source, "Unknown category"); }
    }

    private static int storyArcList(ServerCommandSource source) {
        var arcs = LivelyApi.storyArcs().snapshot().values();
        source.sendFeedback(() -> Text.literal("Story arcs (" + arcs.size() + "): " + arcs.stream().map(a -> a.id() + ":" + a.seed() + ":" + a.state() + ":phase=" + a.phase()).toList()), false);
        return 1;
    }

    private static int storyArcStart(ServerCommandSource source, String seed, int phases, String title) {
        StoryArcEngine.Arc arc = LivelyApi.storyArcs().start(seed, title, phases, Map.of("source", "admin"));
        source.sendFeedback(() -> Text.literal("Started story arc " + arc.id()), false);
        return 1;
    }

    private static int storyArcState(ServerCommandSource source, String rawId, String rawState) {
        try {
            UUID id = UUID.fromString(rawId);
            StoryArcEngine.State state = StoryArcEngine.State.valueOf(rawState.toUpperCase(Locale.ROOT));
            return flag(source, LivelyApi.storyArcs().state(id, state).isPresent(), "story arc state updated");
        } catch (IllegalArgumentException ex) { return sourceError(source, "Invalid story arc UUID/state"); }
    }

    private static int storySeedList(ServerCommandSource source) {
        var seeds = LivelyApi.storySeeds().snapshot().values();
        source.sendFeedback(() -> Text.literal("Story seeds (" + seeds.size() + "): " + seeds.stream().map(s -> s.id() + ":" + s.category() + ":" + s.weight() + ":" + s.enabled()).toList()), false);
        return 1;
    }

    private static int storySeedSet(ServerCommandSource source, String id, String category, double weight, boolean enabled) {
        try {
            WorldEventEngine.Category value = WorldEventEngine.Category.valueOf(category.toUpperCase(Locale.ROOT));
            LivelyApi.storySeeds().register(new StorySeedEngine.Seed(id, value, weight, enabled, Map.of()));
            source.sendFeedback(() -> Text.literal("Story seed updated: " + id), false);
            return 1;
        } catch (IllegalArgumentException ex) { return sourceError(source, "Unknown event category"); }
    }

    private static int storySeedRemove(ServerCommandSource source, String id) {
        LivelyApi.storySeeds().remove(id);
        source.sendFeedback(() -> Text.literal("Story seed removed: " + id), false);
        return 1;
    }

    private static int flag(ServerCommandSource source, boolean ok, String text) {
        if (!ok) return sourceError(source, "Operation failed");
        source.sendFeedback(() -> Text.literal("Lively: " + text), false);
        return 1;
    }

    private static int sourceError(ServerCommandSource source, String message) {
        source.sendError(Text.literal(message));
        return 0;
    }

    private static boolean permit(ServerCommandSource source, String node, int fallback) {
        return LivelyApi.permissions().has(source, node, fallback);
    }

    private static String str(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, String key) {
        return StringArgumentType.getString(ctx, key);
    }

    private static UUID id(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, String key) {
        try { return UUID.fromString(str(ctx, key)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("invalid NPC UUID"); }
    }
}
