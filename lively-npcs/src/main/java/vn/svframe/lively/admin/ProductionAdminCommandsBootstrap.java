package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.quest.QuestRuntime;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Operational command surface that complements the authoring commands in {@link LivelyCommands}. */
public final class ProductionAdminCommandsBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("lively")
                        .then(literal("npc")
                                .then(literal("inspect")
                                        .requires(source -> permitted(source, "lively.admin.npc", 2))
                                        .then(argument("id", StringArgumentType.word())
                                                .executes(ctx -> inspectNpc(ctx.getSource(), uuid(ctx, "id"))))))
                        .then(locationCommands())
                        .then(literal("structure")
                                .then(literal("assign")
                                        .requires(source -> permitted(source, "lively.admin.structure", 2))
                                        .then(argument("structure", StringArgumentType.word())
                                                .then(argument("npc", StringArgumentType.word())
                                                        .then(argument("purpose", StringArgumentType.word())
                                                                .executes(ctx -> assignStructure(ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "structure"),
                                                                        uuid(ctx, "npc"),
                                                                        StringArgumentType.getString(ctx, "purpose"))))))))
                        .then(literal("quest")
                                .then(literal("debug")
                                        .requires(source -> permitted(source, "lively.admin.quest", 2))
                                        .then(argument("id", StringArgumentType.word())
                                                .executes(ctx -> debugQuestId(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))
                        .then(debugCommands()))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> locationCommands() {
        var location = literal("location").requires(source -> permitted(source, "lively.admin.structure", 2));
        location.then(literal("list").executes(ctx -> listLocations(ctx.getSource())));
        location.then(literal("info").then(argument("id", StringArgumentType.word())
                .executes(ctx -> locationInfo(ctx.getSource(), StringArgumentType.getString(ctx, "id")))));
        location.then(literal("set")
                .then(argument("id", StringArgumentType.word())
                        .then(argument("type", StringArgumentType.word())
                                .executes(ctx -> setLocation(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "type"))))));
        location.then(literal("remove").then(argument("id", StringArgumentType.word())
                .executes(ctx -> flag(ctx.getSource(), LivelyApi.structures().remove(StringArgumentType.getString(ctx, "id")), "location removed"))));
        location.then(literal("link")
                .then(argument("id", StringArgumentType.word())
                        .then(argument("parent", StringArgumentType.word())
                                .then(argument("town", StringArgumentType.word())
                                        .executes(ctx -> linkLocation(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "parent"),
                                                StringArgumentType.getString(ctx, "town")))))));
        return location;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> debugCommands() {
        var debug = literal("debug").requires(source -> permitted(source, "lively.admin.debug", 2));
        debug.then(literal("ai").executes(ctx -> debugAi(ctx.getSource())));
        debug.then(literal("path")
                .executes(ctx -> debugPath(ctx.getSource()))
                .then(argument("npc", StringArgumentType.word())
                        .executes(ctx -> debugPathNpc(ctx.getSource(), uuid(ctx, "npc")))));
        debug.then(literal("quest").executes(ctx -> debugQuests(ctx.getSource())));
        debug.then(literal("social").executes(ctx -> debugSocial(ctx.getSource())));
        return debug;
    }

    private static int inspectNpc(ServerCommandSource source, UUID id) {
        if (LivelyApi.npcs() == null) return error(source, "NPC runtime is not active");
        NpcDefinition definition = LivelyApi.npcs().get(id).orElse(null);
        if (definition == null) return error(source, "Unknown NPC");
        String physical = LivelyApi.npcs().body(id)
                .map(body -> body.type() + "/spawned=" + body.spawned() + "/entity=" + body.entityUuid().map(UUID::toString).orElse("none"))
                .orElse("none");
        String state = LivelyApi.states() == null ? "state=unavailable" : LivelyApi.states().get(id)
                .map(value -> {
                    var snapshot = value.snapshot(16);
                    return "revision=" + snapshot.revision()
                            + ", memories=" + snapshot.memories().size()
                            + ", beliefs=" + snapshot.beliefs().size()
                            + ", relationships=" + snapshot.relationships().size();
                }).orElse("state=missing");
        String nav = LivelyApi.worldNavigation() == null ? "none" : LivelyApi.worldNavigation().status(id)
                .map(status -> status.mode() + "/" + status.phase() + "/nodes=" + status.remainingNodes()
                        + (status.reason().isBlank() ? "" : "/reason=" + status.reason()))
                .orElse("none");
        source.sendFeedback(() -> Text.literal("NPC " + id + " name=" + definition.name()
                + " role=" + definition.role() + " body=" + definition.bodyType() + ":" + definition.bodyKey()
                + " physical=" + physical + " nav=" + nav + " " + state), false);
        return 1;
    }

    private static int listLocations(ServerCommandSource source) {
        var structures = LivelyApi.structures().snapshot().structures().values().stream()
                .sorted(java.util.Comparator.comparing(SemanticStructureRegistry.Structure::id)).limit(128).toList();
        source.sendFeedback(() -> Text.literal("Locations (" + LivelyApi.structures().snapshot().structures().size() + "): "
                + structures.stream().map(value -> value.id() + ":" + value.type()).toList()), false);
        return 1;
    }

    private static int locationInfo(ServerCommandSource source, String id) {
        var value = LivelyApi.structures().get(id).orElse(null);
        if (value == null) return error(source, "Unknown location");
        source.sendFeedback(() -> Text.literal("Location " + value.id() + " type=" + value.type()
                + " world=" + value.bounds().world() + " bounds=" + value.bounds()
                + " parent=" + value.parentId() + " town=" + value.townId()
                + " state=" + value.state() + " capabilities=" + value.capabilities()
                + " points=" + value.points()), false);
        return 1;
    }

    private static int setLocation(ServerCommandSource source, String id, String type) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return error(source, "Player selection required");
        var first = SelectionService.first(player.getUuid()).orElse(null);
        var second = SelectionService.second(player.getUuid()).orElse(null);
        if (first == null || second == null) return error(source, "Set /lively pos1 and /lively pos2 first");
        if (!first.world().equals(second.world())) return error(source, "Selection points must be in the same world");
        BlockPos a = first.pos(), b = second.pos();
        var bounds = new SemanticStructureRegistry.Bounds(first.world(),
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        var old = LivelyApi.structures().get(id).orElse(null);
        var value = new SemanticStructureRegistry.Structure(id, type, bounds,
                old == null ? java.util.Set.of() : old.capabilities(),
                old == null ? Map.of() : old.points(),
                old == null ? null : old.parentId(), old == null ? null : old.townId(),
                old == null ? SemanticStructureRegistry.OperationalState.OPEN : old.state(), 0L);
        LivelyApi.structures().register(value);
        if (LivelyApi.structureScanner() != null) LivelyApi.structureScanner().request(id);
        source.sendFeedback(() -> Text.literal("Location updated: " + id + " type=" + type + " bounds=" + bounds), false);
        return 1;
    }

    private static int linkLocation(ServerCommandSource source, String id, String rawParent, String rawTown) {
        String parent = "-".equals(rawParent) ? null : rawParent;
        String town = "-".equals(rawTown) ? null : rawTown;
        if (LivelyApi.structures().get(id).isEmpty()) return error(source, "Unknown location");
        if (parent != null && LivelyApi.structures().get(parent).isEmpty()) return error(source, "Unknown parent location");
        boolean changed = LivelyApi.structures().setMembership(id, parent, town).isPresent();
        return flag(source, changed, "location link updated");
    }

    private static int assignStructure(ServerCommandSource source, String structure, UUID npc, String rawPurpose) {
        if (LivelyApi.npcs() == null || LivelyApi.npcs().get(npc).isEmpty()) return error(source, "Unknown NPC");
        if (LivelyApi.structures().get(structure).isEmpty()) return error(source, "Unknown structure");
        String purpose = rawPurpose.toLowerCase(Locale.ROOT);
        if (!purpose.equals("home") && !purpose.equals("work")) return error(source, "Purpose must be home or work");
        return flag(source, LivelyApi.npcs().setMetadata(npc, purpose + ".structure", structure),
                "structure assigned as " + purpose);
    }

    private static int debugAi(ServerCommandSource source) {
        int definitions = LivelyApi.npcs() == null ? 0 : LivelyApi.npcs().snapshot().size();
        long active = LivelyApi.npcs() == null ? 0L : LivelyApi.npcs().snapshot().values().stream()
                .filter(NpcDefinition::spawned).filter(NpcDefinition::aiEnabled).count();
        var config = LivelyApi.runtimeConfig() == null ? null : LivelyApi.runtimeConfig().current();
        String budgets = config == null ? "config=unavailable" : "decisionsPerPulse=" + config.aiDecisionsPerPulse()
                + ", maxPending=" + config.aiMaxPending() + ", observed=" + config.maxObservedEntities();
        source.sendFeedback(() -> Text.literal("AI: autonomy=" + (LivelyApi.autonomy() != null)
                + ", definitions=" + definitions + ", active=" + active + ", " + budgets), false);
        return 1;
    }

    private static int debugPath(ServerCommandSource source) {
        if (LivelyApi.worldNavigation() == null) return error(source, "Navigation runtime is not active");
        source.sendFeedback(() -> Text.literal("Pathfinding: active=" + LivelyApi.worldNavigation().activeCount()), false);
        return 1;
    }

    private static int debugPathNpc(ServerCommandSource source, UUID npc) {
        if (LivelyApi.worldNavigation() == null) return error(source, "Navigation runtime is not active");
        var status = LivelyApi.worldNavigation().status(npc).orElse(null);
        if (status == null) return error(source, "NPC has no active navigation task");
        source.sendFeedback(() -> Text.literal("Path " + npc + ": mode=" + status.mode() + " phase=" + status.phase()
                + " world=" + status.world() + " target=" + status.target() + " nodes=" + status.remainingNodes()
                + " reason=" + status.reason()), false);
        return 1;
    }

    private static int debugQuests(ServerCommandSource source) {
        var snapshot = LivelyApi.quests().snapshot();
        EnumMap<QuestRuntime.Status, Integer> counts = new EnumMap<>(QuestRuntime.Status.class);
        snapshot.quests().values().forEach(quest -> counts.merge(quest.status(), 1, Integer::sum));
        source.sendFeedback(() -> Text.literal("Quests: revision=" + snapshot.revision() + " total=" + snapshot.quests().size()
                + " byStatus=" + counts), false);
        return 1;
    }

    private static int debugQuestId(ServerCommandSource source, String rawId) {
        final UUID id;
        try { id = UUID.fromString(rawId); }
        catch (IllegalArgumentException error) { return error(source, "Invalid quest UUID"); }
        QuestRuntime.Quest quest = LivelyApi.quests().snapshot().quests().get(id);
        if (quest == null) return error(source, "Unknown quest");
        source.sendFeedback(() -> Text.literal("Quest " + id + " status=" + quest.status() + " owner=" + quest.owner()
                + " issuer=" + quest.issuer() + " progress=" + quest.progress()
                + " available=" + LivelyApi.quests().availableObjectives(quest).stream().map(QuestRuntime.Objective::id).toList()
                + " facts=" + quest.facts()), false);
        return 1;
    }

    private static int debugSocial(ServerCommandSource source) {
        var snapshot = LivelyApi.social().snapshot();
        source.sendFeedback(() -> Text.literal("Social: revision=" + snapshot.revision()
                + " relationships=" + snapshot.relationships().size()
                + " reputationEntries=" + snapshot.reputation().size()
                + " rumors=" + snapshot.rumors().size()), false);
        return 1;
    }

    private static UUID uuid(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, String name) {
        try { return UUID.fromString(StringArgumentType.getString(ctx, name)); }
        catch (IllegalArgumentException error) { throw new com.mojang.brigadier.exceptions.CommandSyntaxException(
                com.mojang.brigadier.exceptions.BuiltInExceptions.INVALID_UUID, Text.literal("Invalid UUID")); }
    }

    private static boolean permitted(ServerCommandSource source, String permission, int vanillaLevel) {
        return LivelyApi.permissions().has(source, permission, vanillaLevel);
    }

    private static int flag(ServerCommandSource source, boolean ok, String message) {
        if (!ok) return error(source, message + " failed");
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }

    private static int error(ServerCommandSource source, String message) {
        source.sendError(Text.literal(message));
        return 0;
    }
}
