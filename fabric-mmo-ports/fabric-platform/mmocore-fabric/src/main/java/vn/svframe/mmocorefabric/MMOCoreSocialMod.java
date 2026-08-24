package vn.svframe.mmocorefabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.mmocorefabric.mixin.MMOCoreFabricAccessor;
import vn.svframe.mmocorefabric.runtime.PlayerSnapshot;
import vn.svframe.mmocorefabric.runtime.social.Guild;
import vn.svframe.mmocorefabric.runtime.social.GuildManager;
import vn.svframe.mmocorefabric.runtime.social.Party;
import vn.svframe.mmocorefabric.runtime.social.PartyManager;
import vn.svframe.mythiclibfabric.runtime.RpgProfileRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Native party/guild invitations, private chat and server-side social menus. */
public final class MMOCoreSocialMod implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("MMOCore-Fabric/Social");
    private static final long INVITE_TIMEOUT_MS = 120_000L;
    private static final Path GUILD_FILE = FabricLoader.getInstance().getConfigDir().resolve("MMOCore").resolve("guilds.bin");
    private static final Map<UUID, PartyInvite> PARTY_INVITES = new ConcurrentHashMap<>();
    private static final Map<UUID, GuildInvite> GUILD_INVITES = new ConcurrentHashMap<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MMOCore-Fabric-SocialIO");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(partyCommands());
            dispatcher.register(guildCommands());
            dispatcher.register(literal("p").then(argument("message", StringArgumentType.greedyString()).executes(ctx -> partyChat(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "message")))));
            dispatcher.register(literal("g").then(argument("message", StringArgumentType.greedyString()).executes(ctx -> guildChat(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "message")))));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            IO.shutdown();
            try { IO.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.server.command.ServerCommandSource> partyCommands() {
        return literal("party")
                .executes(ctx -> openParty(ctx.getSource().getPlayerOrThrow()))
                .then(literal("invite").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                    ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                    PartyManager parties = parties();
                    Party party = parties.partyOf(sender.getUuid()).orElseGet(() -> parties.create(snapshot(sender)));
                    if (!party.owner().equals(sender.getUuid())) return error(sender, "Only the party owner can invite players.");
                    if (target.getUuid().equals(sender.getUuid()) || party.contains(target.getUuid())) return error(sender, "That player is already in your party.");
                    PARTY_INVITES.put(target.getUuid(), new PartyInvite(party.id(), sender.getUuid(), expires()));
                    sender.sendMessage(Text.literal("Party invite sent to " + target.getName().getString() + "."), false);
                    target.sendMessage(Text.literal(sender.getName().getString() + " invited you to a party. Use /party accept or /party deny within 120 seconds."), false);
                    return 1;
                })))
                .then(literal("accept").executes(ctx -> acceptParty(ctx.getSource().getPlayerOrThrow())))
                .then(literal("deny").executes(ctx -> denyParty(ctx.getSource().getPlayerOrThrow())))
                .then(literal("kick").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                    ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                    Party party = parties().partyOf(sender.getUuid()).orElse(null);
                    if (party == null || !party.owner().equals(sender.getUuid())) return error(sender, "Only the party owner can kick members.");
                    if (sender.getUuid().equals(target.getUuid()) || !party.contains(target.getUuid())) return error(sender, "That player is not a kickable party member.");
                    parties().leave(target.getUuid());
                    broadcastParty(party, target.getName().getString() + " was removed from the party.");
                    target.sendMessage(Text.literal("You were removed from the party."), false);
                    return 1;
                })))
                .then(literal("chat").then(argument("message", StringArgumentType.greedyString()).executes(ctx -> partyChat(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "message")))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.server.command.ServerCommandSource> guildCommands() {
        return literal("guild")
                .executes(ctx -> openGuild(ctx.getSource().getPlayerOrThrow()))
                .then(literal("invite").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                    ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                    Guild guild = guilds().guildOf(sender.getUuid()).orElse(null);
                    if (guild == null || !guild.owner().equals(sender.getUuid())) return error(sender, "Only the guild owner can invite players.");
                    if (target.getUuid().equals(sender.getUuid()) || guild.contains(target.getUuid())) return error(sender, "That player is already in your guild.");
                    GUILD_INVITES.put(target.getUuid(), new GuildInvite(guild.id(), sender.getUuid(), expires()));
                    sender.sendMessage(Text.literal("Guild invite sent to " + target.getName().getString() + "."), false);
                    target.sendMessage(Text.literal(sender.getName().getString() + " invited you to " + guild.name() + ". Use /guild accept or /guild deny within 120 seconds."), false);
                    return 1;
                })))
                .then(literal("accept").executes(ctx -> acceptGuild(ctx.getSource().getPlayerOrThrow())))
                .then(literal("deny").executes(ctx -> denyGuild(ctx.getSource().getPlayerOrThrow())))
                .then(literal("kick").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                    ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                    Guild guild = guilds().guildOf(sender.getUuid()).orElse(null);
                    if (guild == null || !guild.owner().equals(sender.getUuid())) return error(sender, "Only the guild owner can kick members.");
                    if (sender.getUuid().equals(target.getUuid()) || !guild.contains(target.getUuid())) return error(sender, "That player is not a kickable guild member.");
                    guilds().removeMember(target.getUuid());
                    saveGuildsAsync();
                    broadcastGuild(guild, target.getName().getString() + " was removed from the guild.");
                    target.sendMessage(Text.literal("You were removed from the guild."), false);
                    return 1;
                })))
                .then(literal("disband").executes(ctx -> {
                    ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
                    Guild guild = guilds().guildOf(sender.getUuid()).orElse(null);
                    if (guild == null || !guild.owner().equals(sender.getUuid())) return error(sender, "Only the guild owner can disband the guild.");
                    var members = guild.members();
                    broadcastGuild(guild, "Guild disbanded by " + sender.getName().getString() + ".");
                    for (UUID member : members) guilds().removeMember(member);
                    saveGuildsAsync();
                    return 1;
                }))
                .then(literal("chat").then(argument("message", StringArgumentType.greedyString()).executes(ctx -> guildChat(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "message")))));
    }

    private static int acceptParty(ServerPlayerEntity player) {
        PartyInvite invite = PARTY_INVITES.remove(player.getUuid());
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) return error(player, "You do not have a valid party invite.");
        Party party = parties().get(invite.partyId).orElse(null);
        if (party == null) return error(player, "That party no longer exists.");
        parties().join(invite.partyId, snapshot(player));
        broadcastParty(party, player.getName().getString() + " joined the party.");
        return 1;
    }

    private static int denyParty(ServerPlayerEntity player) {
        PartyInvite invite = PARTY_INVITES.remove(player.getUuid());
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) return error(player, "You do not have a valid party invite.");
        player.sendMessage(Text.literal("Party invite declined."), false);
        return 1;
    }

    private static int acceptGuild(ServerPlayerEntity player) {
        GuildInvite invite = GUILD_INVITES.remove(player.getUuid());
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) return error(player, "You do not have a valid guild invite.");
        Guild guild = guilds().get(invite.guildId).orElse(null);
        if (guild == null) return error(player, "That guild no longer exists.");
        guilds().addMember(guild.id(), player.getUuid());
        saveGuildsAsync();
        broadcastGuild(guild, player.getName().getString() + " joined the guild.");
        return 1;
    }

    private static int denyGuild(ServerPlayerEntity player) {
        GuildInvite invite = GUILD_INVITES.remove(player.getUuid());
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) return error(player, "You do not have a valid guild invite.");
        player.sendMessage(Text.literal("Guild invite declined."), false);
        return 1;
    }

    private static int partyChat(ServerPlayerEntity player, String message) {
        Party party = parties().partyOf(player.getUuid()).orElse(null);
        if (party == null) return error(player, "You are not in a party.");
        String text = "[Party] " + player.getName().getString() + ": " + message;
        for (var member : party.members()) {
            ServerPlayerEntity online = player.getServer().getPlayerManager().getPlayer(member.id());
            if (online != null) online.sendMessage(Text.literal(text), false);
        }
        return 1;
    }

    private static int guildChat(ServerPlayerEntity player, String message) {
        Guild guild = guilds().guildOf(player.getUuid()).orElse(null);
        if (guild == null) return error(player, "You are not in a guild.");
        String text = "[Guild] " + player.getName().getString() + ": " + message;
        for (UUID id : guild.members()) {
            ServerPlayerEntity online = player.getServer().getPlayerManager().getPlayer(id);
            if (online != null) online.sendMessage(Text.literal(text), false);
        }
        return 1;
    }

    private static int openParty(ServerPlayerEntity player) {
        Party party = parties().partyOf(player.getUuid()).orElse(null);
        if (party == null) return error(player, "You are not in a party. Use /party create or /party invite.");
        SimpleInventory inventory = new SimpleInventory(27);
        int slot = 0;
        for (var member : party.members()) {
            if (slot >= inventory.size()) break;
            ItemStack icon = new ItemStack(member.id().equals(party.owner()) ? Items.GOLDEN_HELMET : Items.PLAYER_HEAD);
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(member.name() + (member.id().equals(party.owner()) ? " [Owner]" : "") + " Lv." + member.level()));
            inventory.setStack(slot++, icon);
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, ignored) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, inventory), Text.literal("Party")));
        return 1;
    }

    private static int openGuild(ServerPlayerEntity player) {
        Guild guild = guilds().guildOf(player.getUuid()).orElse(null);
        if (guild == null) return error(player, "You are not in a guild. Use /guild create <name> <tag>.");
        SimpleInventory inventory = new SimpleInventory(27);
        int slot = 0;
        for (UUID member : guild.members()) {
            if (slot >= inventory.size()) break;
            ServerPlayerEntity online = player.getServer().getPlayerManager().getPlayer(member);
            String name = online == null ? member.toString() : online.getName().getString();
            ItemStack icon = new ItemStack(member.equals(guild.owner()) ? Items.GOLDEN_HELMET : Items.PLAYER_HEAD);
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name + (member.equals(guild.owner()) ? " [Owner]" : "") + (online == null ? " [Offline]" : "")));
            inventory.setStack(slot++, icon);
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, ignored) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, inventory), Text.literal(guild.name() + " [" + guild.tag() + "]")));
        return 1;
    }

    private static void broadcastParty(Party party, String message) {
        var minecraftServer = serverFromAnyPartyMember(party);
        if (minecraftServer == null) return;
        for (var member : party.members()) {
            ServerPlayerEntity online = minecraftServer.getPlayerManager().getPlayer(member.id());
            if (online != null) online.sendMessage(Text.literal("[Party] " + message), false);
        }
    }

    private static void broadcastGuild(Guild guild, String message) {
        var minecraftServer = currentServer();
        if (minecraftServer == null) return;
        for (UUID member : guild.members()) {
            ServerPlayerEntity online = minecraftServer.getPlayerManager().getPlayer(member);
            if (online != null) online.sendMessage(Text.literal("[Guild] " + message), false);
        }
    }

    private static net.minecraft.server.MinecraftServer serverFromAnyPartyMember(Party party) { return currentServer(); }
    private static net.minecraft.server.MinecraftServer currentServer() {
        for (var profile : MMOCoreFabricAccessor.mmocore$getProfiles().keySet()) {
            // Profiles only exist for online players in the core runtime.
            // The first matching player's server is therefore authoritative.
            for (var candidate : net.fabricmc.fabric.api.networking.v1.PlayerLookup.all(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.getGlobalReceivers == null ? null : null)) { break; }
            break;
        }
        return ServerHolder.server;
    }

    private static final class ServerHolder {
        private static volatile net.minecraft.server.MinecraftServer server;
        static { ServerLifecycleEvents.SERVER_STARTED.register(value -> server = value); ServerLifecycleEvents.SERVER_STOPPED.register(value -> server = null); }
    }

    private static PlayerSnapshot snapshot(ServerPlayerEntity player) {
        int level = RpgProfileRegistry.mergeOrDefault(player.getUuid()).level();
        return new PlayerSnapshot(player.getUuid(), player.getName().getString(), level, true,
                player.getServerWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ());
    }

    private static void saveGuildsAsync() {
        IO.execute(() -> {
            try { guilds().save(GUILD_FILE); }
            catch (IOException exception) { LOG.log(Level.SEVERE, "Failed to save guild data", exception); }
        });
    }

    private static PartyManager parties() { return MMOCoreFabricAccessor.mmocore$getParties(); }
    private static GuildManager guilds() { return MMOCoreFabricAccessor.mmocore$getGuilds(); }
    private static long expires() { return System.currentTimeMillis() + INVITE_TIMEOUT_MS; }
    private static int error(ServerPlayerEntity player, String message) { player.sendMessage(Text.literal(message), false); return 0; }

    private record PartyInvite(UUID partyId, UUID inviter, long expiresAt) {}
    private record GuildInvite(String guildId, UUID inviter, long expiresAt) {}
}
