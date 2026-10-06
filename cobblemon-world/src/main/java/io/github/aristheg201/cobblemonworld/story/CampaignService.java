package io.github.aristheg201.cobblemonworld.story;

import io.github.aristheg201.cobblemonworld.notification.NotificationService;
import io.github.aristheg201.cobblemonworld.progression.LevelCapService;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.minecraft.server.level.ServerPlayer;

public final class CampaignService {
    private CampaignService() {}

    public static boolean setFlag(ServerPlayer player, String flag) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (!p.storyFlags.add(flag)) return false;
        ProgressionStore.INSTANCE.save();
        return true;
    }

    public static void unlockContact(ServerPlayer player, String contactId) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        if (p.contacts.add(contactId)) {
            NotificationService.contact(player, contactId);
            ProgressionStore.INSTANCE.save();
        }
    }

    public static void queueMessage(ServerPlayer player, String contactId, String messageId) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        p.contacts.add(contactId);
        if (p.unreadMessages.add(contactId + ":" + messageId)) {
            NotificationService.message(player, contactId);
            ProgressionStore.INSTANCE.save();
        }
    }

    public static void completeChapter(ServerPlayer player, String chapterId) {
        var chapter = ContentRegistry.INSTANCE.chapter(chapterId);
        if (chapter == null) throw new IllegalArgumentException("Unknown chapter: " + chapterId);
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        for (String flag : chapter.completionFlags()) p.storyFlags.add(flag);
        if (chapter.levelCapOnComplete() > p.levelCap) {
            LevelCapService.setCap(player, chapter.levelCapOnComplete());
            NotificationService.levelCap(player, chapter.levelCapOnComplete());
        }
        ProgressionStore.INSTANCE.save();
    }
}
