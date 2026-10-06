package io.github.aristheg201.cobblemonworld.faction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class FactionBridge {
    private FactionBridge() {}

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("factions");
    }

    public static Optional<String> factionName(ServerPlayer player) {
        return factionObject(player).map(faction -> {
            try {
                return (String) faction.getClass().getMethod("getName").invoke(faction);
            } catch (ReflectiveOperationException e) {
                return "";
            }
        }).filter(name -> !name.isBlank());
    }

    public static Optional<UUID> factionId(ServerPlayer player) {
        return factionObject(player).flatMap(faction -> {
            try {
                return Optional.ofNullable((UUID) faction.getClass().getMethod("getID").invoke(faction));
            } catch (ReflectiveOperationException e) {
                return Optional.empty();
            }
        });
    }

    public static boolean isOwner(ServerPlayer player) {
        if (!available()) return false;
        try {
            Object user = user(player);
            if (user == null) return false;
            Object rank = user.getClass().getField("rank").get(user);
            return rank != null && "OWNER".equals(rank.toString());
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static CreateResult create(ServerPlayer player, String requestedName) {
        if (!available()) return new CreateResult(false, "Factions mod is not installed.");
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.length() < 2 || name.length() > 24) {
            return new CreateResult(false, "Faction name must be 2-24 characters.");
        }
        try {
            Object user = user(player);
            if (user == null) return new CreateResult(false, "Unable to resolve Factions user.");
            if ((boolean) user.getClass().getMethod("isInFaction").invoke(user)) {
                return new CreateResult(false, "You are already in a faction.");
            }

            Class<?> factionClass = Class.forName("io.icker.factions.api.persistents.Faction");
            if (factionClass.getMethod("getByName", String.class).invoke(null, name) != null) {
                return new CreateResult(false, "That faction name is already taken.");
            }

            int startingPower = resolveStartingPower();
            Object faction = factionClass
                    .getConstructor(String.class, String.class, String.class, ChatFormatting.class, boolean.class, int.class)
                    .newInstance(name, "Created from the Trainer Phone", "Welcome to " + name,
                            ChatFormatting.WHITE, false, startingPower);
            factionClass.getMethod("add", factionClass).invoke(null, faction);
            UUID factionId = (UUID) factionClass.getMethod("getID").invoke(faction);

            Class<?> rankClass = Class.forName("io.icker.factions.api.persistents.User$Rank");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object ownerRank = Enum.valueOf((Class<? extends Enum>) rankClass.asSubclass(Enum.class), "OWNER");
            user.getClass().getMethod("joinFaction", UUID.class, rankClass).invoke(user, factionId, ownerRank);

            factionClass.getMethod("save").invoke(null);
            user.getClass().getMethod("save").invoke(null);
            return new CreateResult(true, "Created faction " + name + ".");
        } catch (ReflectiveOperationException e) {
            return new CreateResult(false, "Factions integration failed: " + e.getClass().getSimpleName());
        }
    }

    private static Optional<Object> factionObject(ServerPlayer player) {
        if (!available()) return Optional.empty();
        try {
            Object user = user(player);
            if (user == null) return Optional.empty();
            Method isInFaction = user.getClass().getMethod("isInFaction");
            if (!(boolean) isInFaction.invoke(user)) return Optional.empty();
            return Optional.ofNullable(user.getClass().getMethod("getFaction").invoke(user));
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }

    private static Object user(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> userClass = Class.forName("io.icker.factions.api.persistents.User");
        return userClass.getMethod("get", UUID.class).invoke(null, player.getUUID());
    }

    private static int resolveStartingPower() {
        try {
            Class<?> mod = Class.forName("io.icker.factions.FactionsMod");
            Object config = mod.getField("CONFIG").get(null);
            Object power = config.getClass().getField("POWER").get(config);
            int base = power.getClass().getField("BASE").getInt(power);
            int member = power.getClass().getField("MEMBER").getInt(power);
            return base + member;
        } catch (ReflectiveOperationException ignored) {
            return 10;
        }
    }

    public record CreateResult(boolean success, String message) {}
}
