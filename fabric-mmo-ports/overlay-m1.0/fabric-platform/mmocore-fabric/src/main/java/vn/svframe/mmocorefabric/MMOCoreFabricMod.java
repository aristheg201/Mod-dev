package vn.svframe.mmocorefabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.mmocorefabric.runtime.PlayerSnapshot;
import vn.svframe.mmocorefabric.runtime.config.MMOCoreLegacyConfigLoader;
import vn.svframe.mmocorefabric.runtime.gameplay.ClassRuntime;
import vn.svframe.mmocorefabric.runtime.gameplay.ProfessionRuntime;
import vn.svframe.mmocorefabric.runtime.gameplay.QuestRuntime;
import vn.svframe.mmocorefabric.runtime.persistence.MMOCoreProfileStore;
import vn.svframe.mmocorefabric.runtime.progression.PlayerProgress;
import vn.svframe.mmocorefabric.runtime.social.GuildManager;
import vn.svframe.mmocorefabric.runtime.social.PartyManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MMOCoreFabricMod implements ModInitializer {
    public static final String ID = "mmocorefabric";
    private static final Logger LOG = Logger.getLogger("MMOCore-Fabric");
    private static final Map<UUID, PlayerProgress> PROFILES = new ConcurrentHashMap<>();
    private static final ClassRuntime CLASSES = new ClassRuntime();
    private static final ProfessionRuntime PROFESSIONS = new ProfessionRuntime();
    private static final QuestRuntime QUESTS = new QuestRuntime();
    private static final PartyManager PARTIES = new PartyManager();
    private static final GuildManager GUILDS = new GuildManager();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOCore");
    private static final MMOCoreProfileStore STORE = new MMOCoreProfileStore(ROOT.resolve("playerdata"));
    private static volatile MMOCoreLegacyConfigLoader.Result loaded = new MMOCoreLegacyConfigLoader.Result(0, 0, 0);
    private static volatile MinecraftServer server;

    @Override
    public void onInitialize() {
        reloadDefinitions();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraftServer) -> loadPlayer(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, minecraftServer) -> unloadPlayer(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer -> {
            server = minecraftServer;
            try {
                GUILDS.load(ROOT.resolve("guilds.bin"));
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Failed to load guild data", e);
            }
            LOG.info("MMOCore Fabric runtime online; classes=" + loaded.classes() + ", professions=" + loaded.professions() + ", quests=" + loaded.quests());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> {
            for (ServerPlayerEntity player : minecraftServer.getPlayerManager().getPlayerList()) savePlayer(player.getUuid());
            try {
                GUILDS.save(ROOT.resolve("guilds.bin"));
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Failed to save guild data", e);
            }
            server = null;
        });
    }

    public static String definitionSummary() {
        var value = loaded;
        return value.classes() + ":" + value.professions() + ":" + value.quests();
    }

    private static void loadPlayer(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        try {
            MMOCoreProfileStore.Profile stored = STORE.load(id);
            PROFILES.put(id, stored.progress());
            CLASSES.restoreSelection(id, stored.classId());
            PROFESSIONS.restore(id, stored.professions());
            QUESTS.restore(id, stored.quests());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load MMOCore profile for " + player.getName().getString(), e);
            PROFILES.put(id, new PlayerProgress());
        }
    }

    private static void unloadPlayer(ServerPlayerEntity player) {
        savePlayer(player.getUuid());
        PARTIES.leave(player.getUuid());
        PROFILES.remove(player.getUuid());
        PROFESSIONS.forget(player.getUuid());
        QUESTS.forget(player.getUuid());
    }

    private static void savePlayer(UUID id) {
        PlayerProgress progress = PROFILES.get(id);
        if (progress == null) return;
        try {
            STORE.save(id, new MMOCoreProfileStore.Profile(progress, CLASSES.selected(id).orElse(""), PROFESSIONS.snapshot(id), QUESTS.snapshot(id)));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to save MMOCore profile " + id, e);
        }
    }

    private static boolean reloadDefinitions() {
        try {
            loaded = MMOCoreLegacyConfigLoader.load(ROOT, CLASSES, PROFESSIONS, QUESTS);
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to reload MMOCore legacy configs", e);
            return false;
        }
    }

    private static PlayerProgress progress(ServerPlayerEntity player) {
        return PROFILES.computeIfAbsent(player.getUuid(), ignored -> new PlayerProgress());
    }

    private static PlayerSnapshot snapshot(ServerPlayerEntity player) {
        PlayerProgress progress = progress(player);
        return new PlayerSnapshot(player.getUuid(), player.getName().getString(), progress.level(), true,
                player.getServerWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ());
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(mmocoreRoot("mmocore"));
        dispatcher.register(mmocoreRoot("rpg"));
        dispatcher.register(literal("profile").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            PlayerProgress p = progress(player);
            ctx.getSource().sendFeedback(() -> Text.literal("Level " + p.level() + " | EXP " + format(p.exp()) + " | Skill Points " + p.skillPoints() + " | Attribute Points " + p.attributePoints()), false);
            return 1;
        }));
        dispatcher.register(literal("attributes")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    ctx.getSource().sendFeedback(() -> Text.literal("Attributes: " + progress(player).attributes()), false);
                    return 1;
                })
                .then(literal("spend")
                        .then(argument("attribute", StringArgumentType.word())
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                            boolean ok = progress(player).spendAttribute(StringArgumentType.getString(ctx, "attribute"), IntegerArgumentType.getInteger(ctx, "amount"));
                                            if (!ok) {
                                                ctx.getSource().sendError(Text.literal("Not enough attribute points."));
                                                return 0;
                                            }
                                            savePlayer(player.getUuid());
                                            ctx.getSource().sendFeedback(() -> Text.literal("Attribute updated."), false);
                                            return 1;
                                        }))))));
        dispatcher.register(literal("skills").executes(ctx -> {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            PlayerProgress p = progress(player);
            ctx.getSource().sendFeedback(() -> Text.literal("Skills: " + p.skills() + " | Bindings: " + p.bindings()), false);
            return 1;
        }));
        dispatcher.register(partyRoot());
        dispatcher.register(guildRoot());
        dispatcher.register(friendsRoot());
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> mmocoreRoot(String name) {
        return literal(name)
                .then(literal("status").executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal("MMOCore Fabric | definitions=" + definitionSummary() + " | profiles=" + PROFILES.size() + " | parties=" + PARTIES.partyCount() + " | guilds=" + GUILDS.size()), false);
                    return 1;
                }))
                .then(literal("reload").requires(src -> hasPermission(src, "mmocore.admin.reload", 2)).executes(ctx -> {
                    boolean ok = reloadDefinitions();
                    if (!ok) {
                        ctx.getSource().sendError(Text.literal("MMOCore reload failed. Check server log."));
                        return 0;
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal("MMOCore reloaded: " + definitionSummary()), true);
                    return 1;
                }))
                .then(literal("exp").requires(src -> hasPermission(src, "mmocore.admin.exp", 2))
                        .then(literal("give")
                                .then(argument("player", EntityArgumentType.player())
                                        .then(argument("amount", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> {
                                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                    double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                                    int gained = progress(target).addExp(amount, level -> 100.0 + Math.max(0, level - 1) * 20.0);
                                                    savePlayer(target.getUuid());
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Granted " + format(amount) + " EXP to " + target.getName().getString() + "; levels gained=" + gained), true);
                                                    return 1;
                                                })))))
                .then(literal("skillpoints").requires(src -> hasPermission(src, "mmocore.admin.skillpoints", 2))
                        .then(literal("give")
                                .then(argument("player", EntityArgumentType.player())
                                        .then(argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    progress(target).grantSkillPoints(amount);
                                                    savePlayer(target.getUuid());
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Granted " + amount + " skill points to " + target.getName().getString()), true);
                                                    return 1;
                                                })))))
                .then(literal("class").requires(src -> hasPermission(src, "mmocore.admin.class", 2))
                        .then(literal("set")
                                .then(argument("player", EntityArgumentType.player())
                                        .then(argument("class", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                    String classId = StringArgumentType.getString(ctx, "class");
                                                    if (!CLASSES.select(target.getUuid(), classId, progress(target).level())) {
                                                        ctx.getSource().sendError(Text.literal("Unknown or unavailable class: " + classId));
                                                        return 0;
                                                    }
                                                    savePlayer(target.getUuid());
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Class set for " + target.getName().getString() + ": " + classId), true);
                                                    return 1;
                                                })))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> partyRoot() {
        return literal("party")
                .then(literal("create").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    var party = PARTIES.create(snapshot(player));
                    ctx.getSource().sendFeedback(() -> Text.literal("Party created: " + party.id()), false);
                    return 1;
                }))
                .then(literal("leave").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    var result = PARTIES.leave(player.getUuid());
                    if (!result.removed()) {
                        ctx.getSource().sendError(Text.literal("You are not in a party."));
                        return 0;
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal("Left party."), false);
                    return 1;
                }))
                .then(literal("info").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    var party = PARTIES.partyOf(player.getUuid()).orElse(null);
                    if (party == null) {
                        ctx.getSource().sendError(Text.literal("You are not in a party."));
                        return 0;
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal("Party " + party.id() + " | owner=" + party.owner() + " | members=" + party.size()), false);
                    return 1;
                }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> guildRoot() {
        return literal("guild")
                .then(literal("create")
                        .then(argument("name", StringArgumentType.word())
                                .then(argument("tag", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                            try {
                                                var guild = GUILDS.create(player.getUuid(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "tag"));
                                                GUILDS.save(ROOT.resolve("guilds.bin"));
                                                ctx.getSource().sendFeedback(() -> Text.literal("Guild created: " + guild.name() + " [" + guild.tag() + "]"), false);
                                                return 1;
                                            } catch (Exception e) {
                                                ctx.getSource().sendError(Text.literal("Could not create guild: " + e.getMessage()));
                                                return 0;
                                            }
                                        }))))
                .then(literal("leave").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    var result = GUILDS.removeMember(player.getUuid());
                    if (!result.removed()) {
                        ctx.getSource().sendError(Text.literal("You are not in a guild."));
                        return 0;
                    }
                    try {
                        GUILDS.save(ROOT.resolve("guilds.bin"));
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "Failed to save guild data", e);
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal("Left guild."), false);
                    return 1;
                }))
                .then(literal("info").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    var guild = GUILDS.guildOf(player.getUuid()).orElse(null);
                    if (guild == null) {
                        ctx.getSource().sendError(Text.literal("You are not in a guild."));
                        return 0;
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal(guild.name() + " [" + guild.tag() + "] | owner=" + guild.owner() + " | members=" + guild.size()), false);
                    return 1;
                }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> friendsRoot() {
        return literal("friends")
                .then(literal("add").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                    ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity other = EntityArgumentType.getPlayer(ctx, "player");
                    if (self.getUuid().equals(other.getUuid())) {
                        ctx.getSource().sendError(Text.literal("You cannot add yourself."));
                        return 0;
                    }
                    progress(self).addFriend(other.getUuid());
                    savePlayer(self.getUuid());
                    ctx.getSource().sendFeedback(() -> Text.literal("Added friend: " + other.getName().getString()), false);
                    return 1;
                })))
                .then(literal("remove").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                    ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity other = EntityArgumentType.getPlayer(ctx, "player");
                    if (!progress(self).removeFriend(other.getUuid())) {
                        ctx.getSource().sendError(Text.literal("That player is not in your friends list."));
                        return 0;
                    }
                    savePlayer(self.getUuid());
                    ctx.getSource().sendFeedback(() -> Text.literal("Removed friend: " + other.getName().getString()), false);
                    return 1;
                })))
                .then(literal("list").executes(ctx -> {
                    ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                    List<String> names = new ArrayList<>();
                    MinecraftServer current = server;
                    for (UUID id : progress(self).friends()) {
                        ServerPlayerEntity online = current == null ? null : current.getPlayerManager().getPlayer(id);
                        names.add(online == null ? id.toString() : online.getName().getString());
                    }
                    Collections.sort(names);
                    ctx.getSource().sendFeedback(() -> Text.literal("Friends: " + String.join(", ", names)), false);
                    return names.size();
                }));
    }

    private static boolean hasPermission(ServerCommandSource source, String node, int fallbackLevel) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = provider.getMethod("get").invoke(null);
            Object users = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            UUID uuid = source.getPlayerOrThrow().getUuid();
            Object user = users.getClass().getMethod("getUser", UUID.class).invoke(users, uuid);
            if (user != null) {
                Object cached = user.getClass().getMethod("getCachedData").invoke(user);
                Object permissions = cached.getClass().getMethod("getPermissionData").invoke(cached);
                Object result = permissions.getClass().getMethod("checkPermission", String.class).invoke(permissions, node);
                return (boolean) result.getClass().getMethod("asBoolean").invoke(result);
            }
        } catch (Throwable ignored) {
        }
        return source.hasPermissionLevel(fallbackLevel);
    }

    private static String format(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
