package io.github.aristheg201.cobblemonworld.faction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Optional;

public final class FactionBridge {
    private FactionBridge() {}

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("factions");
    }

    public static Optional<String> factionName(ServerPlayer player) {
        if (!available()) return Optional.empty();
        try {
            Class<?> userClass = Class.forName("io.icker.factions.api.persistents.User");
            Method get = userClass.getMethod("get", java.util.UUID.class);
            Object user = get.invoke(null, player.getUUID());
            if (user == null) return Optional.empty();
            Method isInFaction = userClass.getMethod("isInFaction");
            if (!(boolean) isInFaction.invoke(user)) return Optional.empty();
            Object faction = userClass.getMethod("getFaction").invoke(user);
            if (faction == null) return Optional.empty();
            return Optional.ofNullable((String) faction.getClass().getMethod("getName").invoke(faction));
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }
}
