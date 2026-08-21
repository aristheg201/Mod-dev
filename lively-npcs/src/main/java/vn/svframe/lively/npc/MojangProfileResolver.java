package vn.svframe.lively.npc;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.UserCache;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Resolves Mojang profiles off-thread. If Mojang is unreachable, a deterministic default-skin profile is used. */
public final class MojangProfileResolver {
    public CompletableFuture<GameProfile> resolve(MinecraftServer server, UUID npcId, String skinName, String displayName) {
        String lookup = skinName == null || skinName.isBlank() ? displayName : skinName;
        UserCache cache = server.getUserCache();
        if (cache == null || lookup.isBlank()) return CompletableFuture.completedFuture(fallback(npcId, displayName));
        return cache.findByNameAsync(lookup).thenApply(optional -> enrich(server, optional, npcId, displayName));
    }

    private GameProfile enrich(MinecraftServer server, Optional<GameProfile> cached, UUID npcId, String displayName) {
        if (cached.isEmpty()) return fallback(npcId, displayName);
        GameProfile source = cached.get();
        try {
            ProfileResult result = server.getSessionService().fetchProfile(source.getId(), true);
            if (result != null && result.profile() != null) source = result.profile();
        } catch (RuntimeException ignored) {
            // Cached profile remains useful; client will render the Mojang/default skin available for it.
        }
        GameProfile npcProfile = new GameProfile(npcId, safeProfileName(displayName));
        npcProfile.getProperties().putAll(source.getProperties());
        return npcProfile;
    }

    private GameProfile fallback(UUID npcId, String displayName) {
        return new GameProfile(npcId, safeProfileName(displayName));
    }

    public static UUID offlineSkinUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String safeProfileName(String input) {
        String cleaned = input == null ? "LivelyNPC" : input.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isBlank()) cleaned = "LivelyNPC";
        return cleaned.substring(0, Math.min(16, cleaned.length()));
    }
}
