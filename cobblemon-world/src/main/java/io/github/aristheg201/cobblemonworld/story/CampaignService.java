package io.github.aristheg201.cobblemonworld.story;

import io.github.aristheg201.cobblemonworld.notification.NotificationService;
import io.github.aristheg201.cobblemonworld.progression.LevelCapService;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

public final class CampaignService {
    private CampaignService() {}

    public static void initializePhone(ServerPlayer player) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (p.storyFlags.contains("phone_obtained")) return;
        p.currentStory = "prologue";
        p.currentObjective = "Read the first message on your Trainer Phone.";
        ProgressionStore.INSTANCE.save();
        setFlag(player, "phone_obtained");
    }

    public static boolean setFlag(ServerPlayer player, String flag) {
        if (flag == null || flag.isBlank()) return false;
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (!p.storyFlags.add(flag)) return false;
        ProgressionStore.INSTANCE.save();
        evaluateMessageTriggers(player, flag);
        return true;
    }

    private static void evaluateMessageTriggers(ServerPlayer player, String flag) {
        for (var contact : ContentRegistry.INSTANCE.contacts()) {
            if (contact.messages() == null) continue;
            for (var message : contact.messages()) {
                if (flag.equals(message.triggerFlag())) {
                    unlockContact(player, contact.id());
                    queueMessage(player, contact.id(), message.id());
                }
            }
        }
    }

    public static void unlockContact(ServerPlayer player, String contactId) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (p.contacts.add(contactId)) {
            var definition = ContentRegistry.INSTANCE.contact(contactId);
            NotificationService.contact(player, definition == null ? contactId : definition.displayName());
            ProgressionStore.INSTANCE.save();
        }
    }

    public static void queueMessage(ServerPlayer player, String contactId, String messageId) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        String key = messageKey(contactId, messageId);
        if (p.readMessages.contains(key) || p.unreadMessages.contains(key)) return;

        p.contacts.add(contactId);
        if (p.unreadMessages.add(key)) {
            var definition = ContentRegistry.INSTANCE.contact(contactId);
            NotificationService.message(player, definition == null ? contactId : definition.displayName());
            ProgressionStore.INSTANCE.save();
        }
    }

    public static boolean respondToMessage(ServerPlayer player, String contactId, String messageId, int responseIndex) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        String key = messageKey(contactId, messageId);
        if (!p.unreadMessages.contains(key)) return false;

        var message = ContentRegistry.INSTANCE.message(contactId, messageId);
        if (message == null) return false;
        String[] responses = message.responses() == null ? new String[0] : message.responses();
        if (responses.length > 0 && (responseIndex < 0 || responseIndex >= responses.length)) return false;

        p.unreadMessages.remove(key);
        p.readMessages.add(key);
        ProgressionStore.INSTANCE.save();

        if (message.questUnlock() != null && !message.questUnlock().isBlank()) {
            activateQuest(player, message.questUnlock());
        }
        if (message.setFlag() != null && !message.setFlag().isBlank()) {
            setFlag(player, message.setFlag());
        }
        return true;
    }

    public static boolean markRead(ServerPlayer player, String contactId, String messageId) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        String key = messageKey(contactId, messageId);
        if (!p.unreadMessages.remove(key)) return false;
        p.readMessages.add(key);
        ProgressionStore.INSTANCE.save();
        return true;
    }

    public static boolean activateQuest(ServerPlayer player, String questId) {
        var quest = ContentRegistry.INSTANCE.quest(questId);
        if (quest == null) return false;

        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (p.completedSideQuests.contains(questId)) return false;
        if (!p.activeSideQuests.add(questId)) return false;

        p.currentObjective = quest.description();
        NotificationService.objective(player, quest.title());
        ProgressionStore.INSTANCE.save();
        return true;
    }

    public static int recordObjective(ServerPlayer player, String type, String target, int amount) {
        if (amount <= 0) return 0;
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        int changed = 0;

        for (String questId : new ArrayList<>(p.activeSideQuests)) {
            var quest = ContentRegistry.INSTANCE.quest(questId);
            if (quest == null || quest.objectives() == null) continue;

            for (var objective : quest.objectives()) {
                if (!objective.type().equals(type) || !objective.target().equals(target)) continue;
                String key = questId + ":" + objective.id();
                int old = p.questProgress.getOrDefault(key, 0);
                int next = Math.min(objective.amount(), old + amount);
                if (next != old) {
                    p.questProgress.put(key, next);
                    changed += next - old;
                }
            }

            if (isQuestComplete(p, quest)) {
                completeQuest(player, quest);
            }
        }

        if (changed > 0) ProgressionStore.INSTANCE.save();
        return changed;
    }

    private static boolean isQuestComplete(PlayerProgression p, ContentRegistry.QuestDefinition quest) {
        if (quest.objectives() == null || quest.objectives().length == 0) return true;
        for (var objective : quest.objectives()) {
            String key = quest.id() + ":" + objective.id();
            if (p.questProgress.getOrDefault(key, 0) < objective.amount()) return false;
        }
        return true;
    }

    private static void completeQuest(ServerPlayer player, ContentRegistry.QuestDefinition quest) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (!p.activeSideQuests.remove(quest.id())) return;
        p.completedSideQuests.add(quest.id());
        NotificationService.objective(player, "Side Quest Complete: " + quest.title());
        ProgressionStore.INSTANCE.save();
        if (quest.completionFlag() != null && !quest.completionFlag().isBlank()) {
            setFlag(player, quest.completionFlag());
        }
    }

    public static void completeChapter(ServerPlayer player, String chapterId) {
        var chapter = ContentRegistry.INSTANCE.chapter(chapterId);
        if (chapter == null) throw new IllegalArgumentException("Unknown chapter: " + chapterId);
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());

        if (chapter.completionFlags() != null) {
            for (String flag : chapter.completionFlags()) setFlag(player, flag);
        }
        if (chapter.levelCapOnComplete() > p.levelCap) {
            LevelCapService.setCap(player, chapter.levelCapOnComplete());
            NotificationService.levelCap(player, chapter.levelCapOnComplete());
        }
        ProgressionStore.INSTANCE.save();
    }

    public static String messageKey(String contactId, String messageId) {
        return contactId + ":" + messageId;
    }
}
