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
        if (name == null) return new Result(false, "faction.cobblemonworld.invalid_name");
        if (faction(player).isPresent()) return new Result(false, "faction.cobblemonworld.already_member");
        if (FactionStore.INSTANCE.byName(name).isPresent()) return new Result(false, "faction.cobblemonworld.name_taken");

        var data = new FactionStore.FactionData(UUID.randomUUID(), name, player.getUUID());
        FactionStore.INSTANCE.put(data);
        return new Result(true, "faction.cobblemonworld.created", List.of(name));
    }

    public static Result invite(ServerPlayer actor, ServerPlayer target) {
        var faction = faction(actor).orElse(null);
        if (faction == null) return new Result(false, "faction.cobblemonworld.not_member");
        var role = faction.role(actor.getUUID());
        if (role == null || !role.canManageMembers()) return new Result(false, "faction.cobblemonworld.invite_permission");
        if (faction(target).isPresent()) return new Result(false, "faction.cobblemonworld.target_member", List.of(target.getGameProfile().getName()));
        if (faction.members.size() >= CWorldConfig.INSTANCE.factionMaxMembers) return new Result(false, "faction.cobblemonworld.member_limit");
        faction.invites.add(target.getUUID());
        FactionStore.INSTANCE.touch();
        return new Result(true, "faction.cobblemonworld.invited", List.of(target.getGameProfile().getName(), faction.name));
    }

    public static Result accept(ServerPlayer player, String factionName) {
        if (faction(player).isPresent()) return new Result(false, "faction.cobblemonworld.leave_first");
        var faction = FactionStore.INSTANCE.byName(factionName).orElse(null);
        if (faction == null) return new Result(false, "faction.cobblemonworld.not_found");
        if (!faction.invites.contains(player.getUUID())) return new Result(false, "faction.cobblemonworld.no_invite", List.of(faction.name));
        if (faction.members.size() >= CWorldConfig.INSTANCE.factionMaxMembers) return new Result(false, "faction.cobblemonworld.member_limit");
        faction.invites.remove(player.getUUID());
        faction.members.put(player.getUUID(), FactionStore.Role.MEMBER);
        FactionStore.INSTANCE.touch();
        return new Result(true, "faction.cobblemonworld.joined", List.of(faction.name));
    }

    public static Result leave(ServerPlayer player) {
        var faction = faction(player).orElse(null);
        if (faction == null) return new Result(false, "faction.cobblemonworld.not_member");
        if (player.getUUID().equals(faction.owner)) return new Result(false, "faction.cobblemonworld.owner_leave");
        faction.members.remove(player.getUUID());
        faction.invites.remove(player.getUUID());
        FactionStore.INSTANCE.touch();
        return new Result(true, "faction.cobblemonworld.left", List.of(faction.name));
    }

    public static Result kick(ServerPlayer actor, ServerPlayer target) {
        var faction = faction(actor).orElse(null);
        if (faction == null) return new Result(false, "faction.cobblemonworld.not_member");
        var actorRole = faction.role(actor.getUUID());
        var targetRole = faction.role(target.getUUID());
        if (targetRole == null) return new Result(false, "faction.cobblemonworld.not_your_member");
        if (target.getUUID().equals(faction.owner)) return new Result(false, "faction.cobblemonworld.cannot_kick_owner");
        if (actorRole == FactionStore.Role.MEMBER || actorRole == null) return new Result(false, "faction.cobblemonworld.kick_permission");
        if (actorRole == FactionStore.Role.OFFICER && targetRole != FactionStore.Role.MEMBER) {
            return new Result(false, "faction.cobblemonworld.officer_kick");
        }
        faction.members.remove(target.getUUID());
        FactionStore.INSTANCE.touch();
        return new Result(true, "faction.cobblemonworld.removed", List.of(target.getGameProfile().getName(), faction.name));
    }

    public static Result setOfficer(ServerPlayer owner, ServerPlayer target, boolean officer) {
        var faction = faction(owner).orElse(null);
        if (faction == null || !owner.getUUID().equals(faction.owner)) return new Result(false, "faction.cobblemonworld.rank_permission");
        if (!faction.members.containsKey(target.getUUID())) return new Result(false, "faction.cobblemonworld.not_your_member");
        if (target.getUUID().equals(faction.owner)) return new Result(false, "faction.cobblemonworld.owner_rank");
        faction.members.put(target.getUUID(), officer ? FactionStore.Role.OFFICER : FactionStore.Role.MEMBER);
        FactionStore.INSTANCE.touch();
        return new Result(true, officer ? "faction.cobblemonworld.promoted" : "faction.cobblemonworld.demoted", List.of(target.getGameProfile().getName()));
    }

    public static Result transfer(ServerPlayer owner, ServerPlayer target) {
        var faction = faction(owner).orElse(null);
        if (faction == null || !owner.getUUID().equals(faction.owner)) return new Result(false, "faction.cobblemonworld.transfer_permission");
        if (!faction.members.containsKey(target.getUUID())) return new Result(false, "faction.cobblemonworld.target_required");
        if (target.getUUID().equals(faction.owner)) return new Result(false, "faction.cobblemonworld.already_owner");
        faction.members.put(owner.getUUID(), FactionStore.Role.OFFICER);
        faction.owner = target.getUUID();
        faction.members.put(target.getUUID(), FactionStore.Role.OWNER);
        FactionStore.INSTANCE.touch();
        return new Result(true, "faction.cobblemonworld.transferred", List.of(target.getGameProfile().getName()));
    }

    public static Result disband(ServerPlayer owner) {
        var faction = faction(owner).orElse(null);
        if (faction == null || !owner.getUUID().equals(faction.owner)) return new Result(false, "faction.cobblemonworld.disband_permission");
        String name = faction.name;
        FactionStore.INSTANCE.remove(faction.id);
        return new Result(true, "faction.cobblemonworld.disbanded", List.of(name));
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

    public record Result(boolean success, String message, List<String> arguments) {
        public Result(boolean success, String message) { this(success, message, List.of()); }
        public net.minecraft.network.chat.MutableComponent component() {
            return net.minecraft.network.chat.Component.translatable(message, arguments.toArray());
        }
    }
}
