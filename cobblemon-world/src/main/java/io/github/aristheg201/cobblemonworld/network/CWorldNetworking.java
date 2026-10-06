package io.github.aristheg201.cobblemonworld.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.faction.FactionBridge;
import io.github.aristheg201.cobblemonworld.faction.IslandWarService;
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
        switch (payload.action()) {
            case "respond" -> {
                String[] key = splitMessageKey(payload.primary());
                if (key == null) return;
                try {
                    CampaignService.respondToMessage(player, key[0], key[1], Integer.parseInt(payload.secondary()));
                } catch (NumberFormatException ignored) {}
            }
            case "mark_read" -> {
                String[] key = splitMessageKey(payload.primary());
                if (key != null) CampaignService.markRead(player, key[0], key[1]);
            }
            case "track_quest" -> {
                var quest = ContentRegistry.INSTANCE.quest(payload.primary());
                if (quest != null) {
                    PlayerProgression progression = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
                    progression.currentObjective = quest.description();
                    ProgressionStore.INSTANCE.save();
                    toast(player, "objective", "Objective Tracked", quest.title());
                }
            }
            case "faction_create" -> {
                var result = FactionBridge.create(player, payload.primary());
                toast(player, "faction", result.success() ? "Faction Created" : "Faction Error", result.message());
            }
            case "faction_island_join" -> IslandWarService.join(player);
            default -> {}
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

        List<ContactView> contacts = new ArrayList<>();
        for (String id : p.contacts) {
            var contact = ContentRegistry.INSTANCE.contact(id);
            if (contact == null) continue;
            int unread = 0;
            for (String key : p.unreadMessages) if (key.startsWith(id + ":")) unread++;
            contacts.add(new ContactView(id, contact.displayName(), contact.icon(), unread));
        }

        List<QuestView> quests = new ArrayList<>();
        for (String id : p.activeSideQuests) {
            var quest = ContentRegistry.INSTANCE.quest(id);
            if (quest == null) continue;
            int completed = 0;
            int required = 0;
            if (quest.objectives() != null) {
                for (var objective : quest.objectives()) {
                    required += objective.amount();
                    completed += Math.min(objective.amount(),
                            p.questProgress.getOrDefault(id + ":" + objective.id(), 0));
                }
            }
            quests.add(new QuestView(id, quest.title(), quest.giver(), quest.description(), completed, required, false));
        }
        for (String id : p.completedSideQuests) {
            var quest = ContentRegistry.INSTANCE.quest(id);
            if (quest != null) quests.add(new QuestView(id, quest.title(), quest.giver(), quest.description(), 1, 1, true));
        }

        var chapter = ContentRegistry.INSTANCE.chapter(p.currentStory);
        StoryView story = new StoryView(
                p.currentStory,
                chapter == null ? p.currentStory : chapter.title(),
                p.currentObjective
        );

        var island = IslandWarService.statusView(player);
        FactionView faction = new FactionView(
                island.factionName(), island.phase(), island.affinity(), island.ownerFaction(),
                island.gateScore(), island.qualified(), island.ownedControlPoints(), island.islandBuilt(),
                FactionBridge.available()
        );

        PhoneSnapshot snapshot = new PhoneSnapshot(
                player.getGameProfile().getName(),
                p.levelCap,
                story,
                List.copyOf(p.badges),
                List.copyOf(contacts),
                List.copyOf(quests),
                p.leagueTier,
                p.leaguePoints,
                faction,
                List.copyOf(messages)
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
                split[0],
                contact.displayName(),
                message.text(),
                message.responses() == null ? List.of() : List.of(message.responses()),
                unread
        ));
    }

    public static void toast(ServerPlayer player, String category, String title, String body) {
        ServerPlayNetworking.send(player, new ToastPayload(category, title, body));
    }

    public record StoryView(String id, String title, String objective) {}
    public record ContactView(String id, String displayName, String icon, int unread) {}
    public record QuestView(String id, String title, String giver, String description, int progress, int required, boolean completed) {}
    public record MessageView(String key, String contactId, String sender, String text, List<String> responses, boolean unread) {}
    public record FactionView(
            String name, String phase, String affinity, String owner,
            int gateScore, boolean qualified, int controlPoints,
            boolean islandBuilt, boolean integrationAvailable
    ) {}

    public record PhoneSnapshot(
            String trainerName,
            int levelCap,
            StoryView story,
            List<String> badges,
            List<ContactView> contacts,
            List<QuestView> quests,
            String leagueTier,
            int leaguePoints,
            FactionView faction,
            List<MessageView> messages
    ) {}
}
