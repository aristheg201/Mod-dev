package vn.svframe.lively.admin;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.animation.AnimationResult;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.chat.NpcPlayerChatBootstrap;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcReference;
import vn.svframe.lively.npc.NpcRuntime;
import vn.svframe.lively.world.BuiltStructureDiscovery;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Human-facing NPC commands. UUIDs remain accepted as a fallback but are never required. */
public final class NpcHumanCommandsBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("lnpc")
                        .requires(source -> LivelyApi.permissions().has(source, "lively.admin.npc", 2))
                        .executes(ctx -> help(ctx.getSource()))
                        .then(literal("help").executes(ctx -> help(ctx.getSource())))
                        .then(literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(literal("scan").then(literal("here").executes(ctx -> scanHere(ctx.getSource()))))
                        .then(literal("info").then(npcArg("npc").executes(ctx -> info(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc"))))))
                        .then(literal("spawn").then(npcArg("npc").executes(ctx -> spawn(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), true))))
                        .then(literal("despawn").then(npcArg("npc").executes(ctx -> spawn(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), false))))
                        .then(literal("remove").then(npcArg("npc").executes(ctx -> remove(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc"))))))
                        .then(literal("bring").then(npcArg("npc").executes(ctx -> bring(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc"))))))
                        .then(literal("come").then(npcArg("npc").executes(ctx -> come(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc"))))))
                        .then(literal("goto").then(npcArg("npc").then(structureArg("structure")
                                .executes(ctx -> goStructure(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), str(ctx, "structure"))))))
                        .then(literal("home").then(npcArg("npc")
                                .then(literal("here").executes(ctx -> assignHere(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), "home")))
                                .then(structureArg("structure").executes(ctx -> assign(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), "home", str(ctx, "structure"))))))
                        .then(literal("work").then(npcArg("npc")
                                .then(literal("here").executes(ctx -> assignHere(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), "work")))
                                .then(structureArg("structure").executes(ctx -> assign(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), "work", str(ctx, "structure"))))))
                        .then(literal("assign").then(npcArg("npc")
                                .then(literal("home")
                                        .then(literal("here").executes(ctx -> assignHere(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), "home")))
                                        .then(structureArg("structure").executes(ctx -> assign(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), "home", str(ctx, "structure")))))
                                .then(literal("work")
                                        .then(literal("here").executes(ctx -> assignHere(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), "work")))
                                        .then(structureArg("structure").executes(ctx -> assign(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), "work", str(ctx, "structure")))))))
                        .then(literal("unassign").then(npcArg("npc")
                                .then(literal("home").executes(ctx -> assign(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), "home", "")))
                                .then(literal("work").executes(ctx -> assign(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), "work", "")))))
                        .then(literal("say").then(npcArg("npc").then(argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> say(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), str(ctx, "message"))))))
                        .then(literal("animate").then(npcArg("npc").then(argument("animation", StringArgumentType.greedyString())
                                .executes(ctx -> animate(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), str(ctx, "animation"))))))
                        .then(literal("ai").then(npcArg("npc").then(argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> setAi(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), BoolArgumentType.getBool(ctx, "enabled"))))))
                        .then(literal("stationary").then(npcArg("npc").then(argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> stationary(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), BoolArgumentType.getBool(ctx, "enabled"))))))
                        .then(literal("rename").then(npcArg("npc").then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> rename(ctx.getSource(), ref(ctx.getSource(), str(ctx, "npc")), str(ctx, "name"))))))
        ));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> npcArg(String key) {
        return argument(key, StringArgumentType.string()).suggests((ctx, builder) -> {
            List<String> names = NpcReference.names(LivelyApi.npcs()).stream().map(NpcHumanCommandsBootstrap::quote).toList();
            return CommandSource.suggestMatching(names, builder);
        });
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> structureArg(String key) {
        return argument(key, StringArgumentType.word()).suggests((ctx, builder) -> CommandSource.suggestMatching(
                LivelyApi.structures().snapshot().structures().keySet().stream().sorted().toList(), builder));
    }

    private static UUID ref(ServerCommandSource source, String raw) {
        if (LivelyApi.npcs() == null) { source.sendError(Text.literal("Lively NPC runtime is not ready")); return null; }
        NpcReference.Resolution resolution = NpcReference.resolve(LivelyApi.npcs(), raw);
        if (!resolution.found()) { source.sendError(Text.literal(resolution.error())); return null; }
        return resolution.id();
    }

    private static int help(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("/lnpc list | scan here | info <name> | bring <name> | come <name> | goto <name> <structure> | home/work <name> <structure|here> | say <name> <text> | animate <name> <animation> | ai/stationary <name> <true|false> | rename/remove <name>"), false);
        return 1;
    }

    private static int list(ServerCommandSource source) {
        if (LivelyApi.npcs() == null) return fail(source, "NPC runtime is not ready");
        List<NpcDefinition> values = LivelyApi.npcs().snapshot().values().stream()
                .sorted(Comparator.comparing(NpcDefinition::name, String.CASE_INSENSITIVE_ORDER)).toList();
        if (values.isEmpty()) { source.sendFeedback(() -> Text.literal("No Lively NPCs"), false); return 1; }
        source.sendFeedback(() -> Text.literal("NPCs: " + values.stream()
                .map(npc -> npc.name() + " [" + npc.role() + ", " + npc.bodyType().name().toLowerCase(Locale.ROOT) + (npc.spawned() ? ", spawned" : ", despawned") + "]")
                .toList()), false);
        return 1;
    }

    private static int info(ServerCommandSource source, UUID id) {
        if (id == null) return 0;
        NpcDefinition npc = LivelyApi.npcs().get(id).orElse(null);
        if (npc == null) return fail(source, "Unknown NPC");
        String home = npc.metadata().getOrDefault("home.structure", "-");
        String work = npc.metadata().getOrDefault("work.structure", "-");
        source.sendFeedback(() -> Text.literal(npc.name() + " | role=" + npc.role() + " | body=" + npc.bodyType()
                + " | spawned=" + npc.spawned() + " | ai=" + npc.aiEnabled() + " | home=" + home + " | work=" + work
                + " | stationary=" + npc.metadata().getOrDefault("behavior.stationary", "false")), false);
        return 1;
    }

    private static int spawn(ServerCommandSource source, UUID id, boolean value) {
        if (id == null) return 0;
        boolean ok = value ? LivelyApi.npcs().spawn(source.getServer(), id) : LivelyApi.npcs().despawn(source.getServer(), id);
        return result(source, ok, value ? "spawned" : "despawned");
    }

    private static int remove(ServerCommandSource source, UUID id) {
        return id == null ? 0 : result(source, LivelyApi.npcs().remove(source.getServer(), id), "removed");
    }

    private static int bring(ServerCommandSource source, UUID id) {
        if (id == null) return 0;
        return result(source, LivelyApi.npcs().teleport(source.getServer(), id,
                source.getWorld().getRegistryKey().getValue().toString(), source.getPosition(), source.getRotation().y, source.getRotation().x), "brought here");
    }

    private static int come(ServerCommandSource source, UUID id) {
        if (id == null) return 0;
        return result(source, LivelyApi.worldNavigation().goTo(id,
                source.getWorld().getRegistryKey().getValue().toString(), source.getPosition()), "coming here");
    }

    private static int goStructure(ServerCommandSource source, UUID id, String structure) {
        if (id == null) return 0;
        if (LivelyApi.structures().get(structure).isEmpty()) return fail(source, "Unknown structure: " + structure);
        return result(source, LivelyApi.worldNavigation().goToStructure(id, structure), "going to " + structure);
    }

    private static int scanHere(ServerCommandSource source) {
        BuiltStructureDiscovery.Result discovered = BuiltStructureDiscovery.discoverAndRegister(
                source.getWorld(), BlockPos.ofFloored(source.getPosition()));
        if (!discovered.success() || discovered.structure() == null) {
            return fail(source, "Could not discover a building here: " + discovered.detail());
        }
        SemanticStructureRegistry.Structure structure = discovered.structure();
        source.sendFeedback(() -> Text.literal("Lively: " + (discovered.status() == BuiltStructureDiscovery.Status.ALREADY_REGISTERED
                ? "already inside " : "discovered ") + structure.id() + " [" + structure.type() + "] cells="
                + discovered.interiorCells()), false);
        return 1;
    }

    private static int assignHere(ServerCommandSource source, UUID id, String kind) {
        if (id == null) return 0;
        List<SemanticStructureRegistry.Structure> here = LivelyApi.structures().at(
                source.getWorld().getRegistryKey().getValue().toString(),
                source.getPosition().x, source.getPosition().y, source.getPosition().z);
        SemanticStructureRegistry.Structure structure;
        if (here.isEmpty()) {
            BuiltStructureDiscovery.Result discovered = BuiltStructureDiscovery.discoverAndRegister(
                    source.getWorld(), BlockPos.ofFloored(source.getPosition()));
            if (!discovered.success() || discovered.structure() == null) {
                return fail(source, "No registered structure here and automatic building discovery failed: " + discovered.detail());
            }
            structure = discovered.structure();
            source.sendFeedback(() -> Text.literal("Lively: discovered player-built " + structure.type() + " as " + structure.id()), false);
        } else structure = here.getFirst();
        return assign(source, id, kind, structure.id());
    }

    private static int assign(ServerCommandSource source, UUID id, String kind, String structure) {
        if (id == null) return 0;
        if (!structure.isBlank() && LivelyApi.structures().get(structure).isEmpty()) return fail(source, "Unknown structure: " + structure);
        String key = kind.equals("home") ? "home.structure" : "work.structure";
        boolean ok = LivelyApi.npcs().setMetadata(id, key, structure);
        String npcName = LivelyApi.npcs().get(id).map(NpcDefinition::name).orElse("NPC");
        return result(source, ok, structure.isBlank() ? npcName + " " + kind + " cleared" : npcName + " " + kind + " = " + structure);
    }

    private static int say(ServerCommandSource source, UUID id, String message) {
        if (id == null) return 0;
        return result(source, NpcPlayerChatBootstrap.say(source.getServer(), id, message), "message sent");
    }

    private static int animate(ServerCommandSource source, UUID id, String animation) {
        if (id == null) return 0;
        if (LivelyApi.animations() == null) return fail(source, "Animation engine is not ready");
        AnimationResult result = LivelyApi.animations().play(source.getServer(), id, animation);
        return result(source, result.accepted(), result.accepted() ? result.detail() : "Animation rejected: " + result.detail());
    }

    private static int setAi(ServerCommandSource source, UUID id, boolean enabled) {
        return id == null ? 0 : result(source, LivelyApi.npcs().setFlag(id, NpcRuntime.Flag.AI, enabled), "AI=" + enabled);
    }

    private static int stationary(ServerCommandSource source, UUID id, boolean enabled) {
        return id == null ? 0 : result(source, LivelyApi.npcs().setMetadata(id, "behavior.stationary", Boolean.toString(enabled)), "stationary=" + enabled);
    }

    private static int rename(ServerCommandSource source, UUID id, String rawName) {
        if (id == null) return 0;
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank() || name.length() > 64) return fail(source, "NPC name must be 1-64 characters");
        boolean duplicate = LivelyApi.npcs().snapshot().values().stream()
                .anyMatch(value -> !value.id().equals(id) && value.name().equalsIgnoreCase(name));
        if (duplicate) return fail(source, "Another NPC already uses that name. Names should stay unique so commands stay sane.");
        NpcDefinition old = LivelyApi.npcs().get(id).orElse(null);
        if (old == null) return fail(source, "Unknown NPC");
        return result(source, LivelyApi.npcs().rename(id, name, old.role()), "renamed to " + name);
    }

    private static int result(ServerCommandSource source, boolean ok, String message) {
        if (!ok) return fail(source, "Operation failed: " + message);
        source.sendFeedback(() -> Text.literal("Lively: " + message), false);
        return 1;
    }

    private static int fail(ServerCommandSource source, String message) {
        source.sendError(Text.literal(message));
        return 0;
    }

    private static String str(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, String key) {
        return StringArgumentType.getString(ctx, key);
    }

    private static String quote(String value) {
        return value.indexOf(' ') >= 0 ? "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : value;
    }
}
