package io.github.aristheg201.cobblemonworld.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.faction.NativeFactionService;
import io.github.aristheg201.cobblemonworld.faction.IslandWarService;
import io.github.aristheg201.cobblemonworld.integration.SvFrameRpgBridge;
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
        PayloadTypeRegistry.playS2C().register(NavigationPayload.TYPE, NavigationPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShopSnapshotPayload.TYPE, ShopSnapshotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShopBuyPayload.TYPE, ShopBuyPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ShopBuyPayload.TYPE, (payload, context) ->
                context.server().execute(() -> io.github.aristheg201.cobblemonworld.shop.ShopService.buy(context.player(), payload)));
        PayloadTypeRegistry.playS2C().register(PhoneSnapshotPayload.TYPE, PhoneSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ToastPayload.TYPE, ToastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QaControlPayload.TYPE, QaControlPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PhoneActionPayload.TYPE, PhoneActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QaAckPayload.TYPE, QaAckPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PhoneActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    handleAction(player, payload);
                    openPhone(player);
                }));

        ServerPlayNetworking.registerGlobalReceiver(QaAckPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    if (Boolean.getBoolean("cworld.qa.server")) {
                        if (Boolean.getBoolean("cworld.qa.production")) io.github.aristheg201.cobblemonworld.qa.ProductionQaServer.ack(payload);
                        else io.github.aristheg201.cobblemonworld.qa.CWorldQaServerHarness.onAck(context.player(), payload);
                    }
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
            case "pin_objective", "track_quest" -> {
                if (io.github.aristheg201.cobblemonworld.story.ObjectiveService.pin(player, payload.primary()))
                    toast(player, "objective", "objective.cobblemonworld.pinned", "objective.cobblemonworld.pinned_hint");
            }
            case "faction_create" -> {
                var result = NativeFactionService.create(player, payload.primary());
                toast(player, "faction", result.success() ? "toast.cobblemonworld.faction_created" : "toast.cobblemonworld.faction_error", result.component());
            }
            case "faction_invite" -> {
                ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(payload.primary());
                var result = target == null
                        ? new NativeFactionService.Result(false, "faction.cobblemonworld.offline")
                        : NativeFactionService.invite(player, target);
                toast(player, "faction", result.success() ? "toast.cobblemonworld.faction_invite" : "toast.cobblemonworld.faction_error", result.component());
            }
            case "faction_accept" -> {
                var result = NativeFactionService.accept(player, payload.primary());
                toast(player, "faction", result.success() ? "toast.cobblemonworld.faction_joined" : "toast.cobblemonworld.faction_error", result.component());
            }
            case "faction_leave" -> {
                var result = NativeFactionService.leave(player);
                toast(player, "faction", result.success() ? "toast.cobblemonworld.faction_left" : "toast.cobblemonworld.faction_error", result.component());
            }
            case "faction_disband" -> {
                var result = NativeFactionService.disband(player);
                toast(player, "faction", result.success() ? "toast.cobblemonworld.faction_disbanded" : "toast.cobblemonworld.faction_error", result.component());
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

        io.github.aristheg201.cobblemonworld.story.ObjectiveService.resolve(p);
        List<MessageView> messages = io.github.aristheg201.cobblemonworld.story.DialogueService.views(p);

        List<ContactView> contacts = new ArrayList<>();
        for (String id : p.contacts) {
            var contact = ContentRegistry.INSTANCE.contact(id);
            if (contact == null) continue;
            int unread = 0;
            for (String key : p.unreadMessages) if (key.startsWith(id + ":")) unread++;
            contacts.add(new ContactView(id, io.github.aristheg201.cobblemonworld.story.DialogueService.contactName(p, id), contact.icon(), unread));
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
            quests.add(new QuestView(id, quest.title(), giverName(quest.giver()), quest.description(), completed, required, false));
        }
        for (String id : p.completedSideQuests) {
            var quest = ContentRegistry.INSTANCE.quest(id);
            if (quest != null) quests.add(new QuestView(id, quest.title(), giverName(quest.giver()), quest.description(), 1, 1, true));
        }

        var chapter = ContentRegistry.INSTANCE.chapter(p.currentStory);
        StoryView story = new StoryView(
                p.currentStory,
                chapter == null ? "story.cobblemonworld.complete.title" : chapter.title(),
                p.currentObjective
        );

        var island = IslandWarService.statusView(player);
        var rpg = SvFrameRpgBridge.snapshot(player);
        var nativeFaction = NativeFactionService.faction(player).orElse(null);
        var nativeRole = NativeFactionService.role(player);
        FactionView faction = new FactionView(
                island.factionName(), island.phase(), island.affinity(), island.ownerFaction(),
                island.gateScore(), island.qualified(), island.ownedControlPoints(), island.islandBuilt(),
                nativeRole == null ? "" : nativeRole.name(),
                nativeFaction == null ? 0 : nativeFaction.memberCount(),
                NativeFactionService.pendingInvites(player)
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
                new RpgView(rpg.available(), rpg.classId(), rpg.level(), rpg.mana(), rpg.maxMana(), rpg.stamina(), rpg.maxStamina(),
                        rpg.strength(), rpg.dexterity(), rpg.intelligence(), rpg.cooldownReduction()),
                faction,
                List.copyOf(messages)
        );
        ServerPlayNetworking.send(player, new PhoneSnapshotPayload(GSON.toJson(snapshot)));
    }

    private static String giverName(String id) {
        var definition = io.github.aristheg201.cobblemonworld.npc.NpcDefinitionRegistry.INSTANCE.get(id);
        if (definition != null) return definition.displayName();
        var contact = ContentRegistry.INSTANCE.contact(id);
        return contact == null ? "" : contact.displayName();
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
        toast(player, category, title, net.minecraft.network.chat.Component.translatable(body));
    }

    public static void toast(ServerPlayer player, String category, String title, net.minecraft.network.chat.Component body) {
        ServerPlayNetworking.send(player, new ToastPayload(category, net.minecraft.network.chat.Component.translatable(title), body));
    }

    public record StoryView(String id, String title, String objective) {}
    public record ContactView(String id, String displayName, String icon, int unread) {}
    public record QuestView(String id, String title, String giver, String description, int progress, int required, boolean completed) {}
    public record MessageView(String key, String contactId, String sender, String text, List<String> responses, boolean unread) {}
    public record RpgView(
            boolean available, String classId, int level,
            double mana, double maxMana, double stamina, double maxStamina,
            int strength, int dexterity, int intelligence, double cooldownReduction
    ) {}

    public record FactionView(
            String name, String phase, String affinity, String owner,
            int gateScore, boolean qualified, int controlPoints,
            boolean islandBuilt, String role, int memberCount, List<String> pendingInvites
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
            RpgView rpg,
            FactionView faction,
            List<MessageView> messages
    ) {}
}
