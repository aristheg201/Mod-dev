package io.github.aristheg201.cobblemonworld.faction;

import io.github.aristheg201.cobblemonworld.config.CWorldConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class NativeFactionService {
    private NativeFactionService() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(FactionStore.INSTANCE::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> FactionStore.INSTANCE.save());

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer victim)) return true;
            if (!(source.getEntity() instanceof ServerPlayer attacker)) return true;
            if (victim.getUUID().equals(attacker.getUUID())) return true;
            if (!sameFaction(victim, attacker)) return true;
            return CWorldConfig.INSTANCE.factionFriendlyFire;
        });
    }

    public static Optional<FactionStore.FactionData> faction(ServerPlayer player) {
        return FactionStore.INSTANCE.byPlayer(player.getUUID());
    }

    public static Optional<String> factionName(ServerPlayer player) {
        return faction(player).map(f -> f.name);
    }

    public static FactionStore.Role role(ServerPlayer player) {
        return faction(player).map(f -> f.role(player.getUUID())).orElse(null);
    }

    public static boolean sameFaction(ServerPlayer a, ServerPlayer b) {
        Optional<FactionStore.FactionData> fa = faction(a);
        Optional<FactionStore.FactionData> fb = faction(b);
        return fa.isPresent() && fb.isPresent() && fa.get().id.equals(fb.get().id);
    }

    public static Result create(ServerPlayer player, String requestedName) {
        String name = sanitizeName(requestedName);
        if (name == null) return new Result(false, "Faction name must be 2-24 characters and use letters, numbers, spaces, _ or -.");
        if (faction(player).isPresent()) return new Result(false, "You are already in a faction.");
        if (FactionStore.INSTANCE.byName(name).isPresent()) return new Result(false, "That faction name is already taken.");

        var data = new FactionStore.FactionData(UUID.randomUUID(), name, player.getUUID());
        FactionStore.INSTANCE.put(data);
        return new Result(true, "Created faction " + name + ".");
    }

    public static Result invite(ServerPlayer actor, ServerPlayer target) {
        var faction = faction(actor).orElse(null);
        if (faction == null) return new Result(false, "You are not in a faction.");
        var role = faction.role(actor.getUUID());
        if (role == null || !role.canManageMembers()) return new Result(false, "Only owners and officers can invite players.");
        if (faction(target).isPresent()) return new Result(false, target.getGameProfile().getName() + " is already in a faction.");
        if (faction.members.size() >= CWorldConfig.INSTANCE.factionMaxMembers) return new Result(false, "Faction member limit reached.");
        faction.invites.add(target.getUUID());
        FactionStore.INSTANCE.touch();
        return new Result(true, "Invited " + target.getGameProfile().getName() + " to " + faction.name + ".");
    }

    public static Result accept(ServerPlayer player, String factionName) {
        if (faction(player).isPresent()) return new Result(false, "Leave your current faction first.");
        var faction = FactionStore.INSTANCE.byName(factionName).orElse(null);
        if (faction == null) return new Result(false, "Faction not found.");
        if (!faction.invites.contains(player.getUUID())) return new Result(false, "You do not have an invite from " + faction.name + ".");
        if (faction.members.size() >= CWorldConfig.INSTANCE.factionMaxMembers) return new Result(false, "Faction member limit reached.");
        faction.invites.remove(player.getUUID());
        faction.members.put(player.getUUID(), FactionStore.Role.MEMBER);
        FactionStore.INSTANCE.touch();
        return new Result(true, "Joined faction " + faction.name + ".");
    }

    public static Result leave(ServerPlayer player) {
        var faction = faction(player).orElse(null);
        if (faction == null) return new Result(false, "You are not in a faction.");
        if (player.getUUID().equals(faction.owner)) return new Result(false, "The owner must transfer ownership or disband the faction.");
        faction.members.remove(player.getUUID());
        faction.invites.remove(player.getUUID());
        FactionStore.INSTANCE.touch();
        return new Result(true, "Left faction " + faction.name + ".");
    }

    public static Result kick(ServerPlayer actor, ServerPlayer target) {
        var faction = faction(actor).orElse(null);
        if (faction == null) return new Result(false, "You are not in a faction.");
        var actorRole = faction.role(actor.getUUID());
        var targetRole = faction.role(target.getUUID());
        if (targetRole == null) return new Result(false, "That player is not in your faction.");
        if (target.getUUID().equals(faction.owner)) return new Result(false, "The owner cannot be kicked.");
        if (actorRole == FactionStore.Role.MEMBER || actorRole == null) return new Result(false, "You cannot kick members.");
        if (actorRole == FactionStore.Role.OFFICER && targetRole != FactionStore.Role.MEMBER) {
            return new Result(false, "Officers can only kick members.");
        }
        faction.members.remove(target.getUUID());
        FactionStore.INSTANCE.touch();
        return new Result(true, "Removed " + target.getGameProfile().getName() + " from " + faction.name + ".");
    }

    public static Result setOfficer(ServerPlayer owner, ServerPlayer target, boolean officer) {
        var faction = faction(owner).orElse(null);
        if (faction == null || !owner.getUUID().equals(faction.owner)) return new Result(false, "Only the faction owner can change officer rank.");
        if (!faction.members.containsKey(target.getUUID())) return new Result(false, "That player is not in your faction.");
        if (target.getUUID().equals(faction.owner)) return new Result(false, "Owner rank cannot be changed.");
        faction.members.put(target.getUUID(), officer ? FactionStore.Role.OFFICER : FactionStore.Role.MEMBER);
        FactionStore.INSTANCE.touch();
        return new Result(true, target.getGameProfile().getName() + (officer ? " promoted to officer." : " demoted to member."));
    }

    public static Result transfer(ServerPlayer owner, ServerPlayer target) {
        var faction = faction(owner).orElse(null);
        if (faction == null || !owner.getUUID().equals(faction.owner)) return new Result(false, "Only the faction owner can transfer ownership.");
        if (!faction.members.containsKey(target.getUUID())) return new Result(false, "Target must be a faction member.");
        if (target.getUUID().equals(faction.owner)) return new Result(false, "Target is already the owner.");
        faction.members.put(owner.getUUID(), FactionStore.Role.OFFICER);
        faction.owner = target.getUUID();
        faction.members.put(target.getUUID(), FactionStore.Role.OWNER);
        FactionStore.INSTANCE.touch();
        return new Result(true, "Transferred ownership to " + target.getGameProfile().getName() + ".");
    }

    public static Result disband(ServerPlayer owner) {
        var faction = faction(owner).orElse(null);
        if (faction == null || !owner.getUUID().equals(faction.owner)) return new Result(false, "Only the faction owner can disband the faction.");
        String name = faction.name;
        FactionStore.INSTANCE.remove(faction.id);
        return new Result(true, "Disbanded faction " + name + ".");
    }

    public static List<String> pendingInvites(ServerPlayer player) {
        List<String> names = new ArrayList<>();
        for (var faction : FactionStore.INSTANCE.all()) {
            if (faction.invites.contains(player.getUUID())) names.add(faction.name);
        }
        return names;
    }

    private static String sanitizeName(String raw) {
        if (raw == null) return null;
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 24) return null;
        if (!name.matches("[A-Za-z0-9 _-]+")) return null;
        return name;
    }

    public record Result(boolean success, String message) {}
}
