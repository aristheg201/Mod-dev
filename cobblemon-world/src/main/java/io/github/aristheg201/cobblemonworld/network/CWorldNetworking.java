package io.github.aristheg201.cobblemonworld.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.faction.FactionBridge;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class CWorldNetworking {
    private static final Gson GSON = new GsonBuilder().create();
    private CWorldNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(PhoneSnapshotPayload.TYPE, PhoneSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ToastPayload.TYPE, ToastPayload.CODEC);
    }

    public static void openPhone(ServerPlayer player) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        PhoneSnapshot snapshot = new PhoneSnapshot(
                player.getGameProfile().getName(),
                p.levelCap,
                p.currentStory,
                p.currentObjective,
                List.copyOf(p.badges),
                List.copyOf(p.contacts),
                List.copyOf(p.unreadMessages),
                List.copyOf(p.activeSideQuests),
                List.copyOf(p.completedSideQuests),
                p.leagueTier,
                p.leaguePoints,
                FactionBridge.factionName(player).orElse("No Faction")
        );
        ServerPlayNetworking.send(player, new PhoneSnapshotPayload(GSON.toJson(snapshot)));
    }

    public static void toast(ServerPlayer player, String category, String title, String body) {
        ServerPlayNetworking.send(player, new ToastPayload(category, title, body));
    }

    public record PhoneSnapshot(
            String trainerName,
            int levelCap,
            String currentStory,
            String currentObjective,
            List<String> badges,
            List<String> contacts,
            List<String> unreadMessages,
            List<String> activeSideQuests,
            List<String> completedSideQuests,
            String leagueTier,
            int leaguePoints,
            String factionName
    ) {}
}
