package io.github.aristheg201.cobblemonworld.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.faction.FactionBridge;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import io.github.aristheg201.cobblemonworld.story.CampaignService;
import io.github.aristheg201.cobblemonworld.story.ContentRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class CWorldNetworking {
    private static final Gson GSON = new GsonBuilder().create();
    private CWorldNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(PhoneSnapshotPayload.TYPE, PhoneSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ToastPayload.TYPE, ToastPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PhoneActionPayload.TYPE, PhoneActionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PhoneActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    handleAction(player, payload);
                    openPhone(player);
                }));
    }

    private static void handleAction(ServerPlayer player, PhoneActionPayload payload) {
        if ("respond".equals(payload.action())) {
            String[] key = splitMessageKey(payload.primary());
            if (key == null) return;
            try {
                int index = Integer.parseInt(payload.secondary());
                CampaignService.respondToMessage(player, key[0], key[1], index);
            } catch (NumberFormatException ignored) {
            }
        } else if ("mark_read".equals(payload.action())) {
            String[] key = splitMessageKey(payload.primary());
            if (key != null) CampaignService.markRead(player, key[0], key[1]);
        } else if ("track_quest".equals(payload.action())) {
            var quest = ContentRegistry.INSTANCE.quest(payload.primary());
            if (quest != null) {
                PlayerProgression progression = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
                progression.currentObjective = quest.description();
                ProgressionStore.INSTANCE.save();
            }
        }
    }

    private static String[] splitMessageKey(String key) {
        int colon = key == null ? -1 : key.indexOf(':');
        if (colon <= 0 || colon >= key.length() - 1) return null;
        return new String[] {key.substring(0, colon), key.substring(colon + 1)};
    }

    public static void openPhone(ServerPlayer player) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        List<MessageView> messages = new ArrayList<>();
        for (String key : p.readMessages) addMessage(messages, key, false);
        for (String key : p.unreadMessages) addMessage(messages, key, true);

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
                FactionBridge.factionName(player).orElse("No Faction"),
                messages
        );
        ServerPlayNetworking.send(player, new PhoneSnapshotPayload(GSON.toJson(snapshot)));
    }

    private static void addMessage(List<MessageView> output, String key, boolean unread) {
        String[] split = splitMessageKey(key);
        if (split == null) return;
        var contact = ContentRegistry.INSTANCE.contact(split[0]);
        var message = ContentRegistry.INSTANCE.message(split[0], split[1]);
        if (contact == null || message == null) return;
        output.add(new MessageView(
                key,
                contact.displayName(),
                message.text(),
                message.responses() == null ? List.of() : List.of(message.responses()),
                unread
        ));
    }

    public static void toast(ServerPlayer player, String category, String title, String body) {
        ServerPlayNetworking.send(player, new ToastPayload(category, title, body));
    }

    public record MessageView(String key, String sender, String text, List<String> responses, boolean unread) {}

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
            String factionName,
            List<MessageView> messages
    ) {}
}
