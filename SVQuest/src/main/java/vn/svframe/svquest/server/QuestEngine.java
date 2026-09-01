package vn.svframe.svquest.server;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.util.Locale;
import java.util.Map;

/** Server-authoritative quest engine. Objective event keys come directly from config/integration producers. */
public final class QuestEngine {
    private final QuestStateStore store;
    private final RewardDispatcher rewards;
    private StateSync sync = player -> {};

    @FunctionalInterface public interface StateSync { void send(ServerPlayerEntity player); }

    public QuestEngine(QuestStateStore store, RewardDispatcher rewards) { this.store = store; this.rewards = rewards; }
    public void setSync(StateSync sync) { this.sync = sync == null ? player -> {} : sync; }

    public void signal(ServerPlayerEntity player, String key) { signal(player, key, 1); }
    public void signal(ServerPlayerEntity player, String key, long amount) { emit(player, key, amount, Map.of()); }
    public void metric(ServerPlayerEntity player, String key, long value) { absolute(player, key, value, Map.of()); }

    public void emit(ServerPlayerEntity player, String type) { emit(player, type, 1, Map.of()); }
    public void emit(ServerPlayerEntity player, String type, long amount) { emit(player, type, amount, Map.of()); }
    public void emit(ServerPlayerEntity player, String type, long amount, Map<String, String> meta) {
        String normalized = normalize(type);
        if (player == null || normalized.isBlank() || amount <= 0) return;
        mutate(player, state -> state.emit(normalized, amount, meta == null ? Map.of() : meta), "event:" + normalized);
    }

    public void absolute(ServerPlayerEntity player, String type, long value, Map<String, String> meta) {
        String normalized = normalize(type);
        if (player == null || normalized.isBlank()) return;
        mutate(player, state -> state.absolute(normalized, value, meta == null ? Map.of() : meta), "absolute:" + normalized);
    }

    /** Manual, server-authoritative reward claim. Completing objectives never grants rewards by itself. */
    public void claim(ServerPlayerEntity player, String questId) {
        if (player == null || questId == null || questId.isBlank() || questId.length() > 96) return;
        try {
            QuestCatalog.Quest quest = QuestCatalog.byId(questId.trim());
            QuestStateStore.PlayerState state = store.get(player.getUuid());
            if (quest == null || state.claimed(quest.id())) {
                sync.send(player);
                return;
            }
            if (!state.unlocked(quest)) {
                player.sendMessage(Text.literal("§cNhiệm vụ này chưa được mở khóa."), false);
                sync.send(player);
                return;
            }
            if (!state.complete(quest)) {
                player.sendMessage(Text.literal("§eNhiệm vụ này chưa hoàn thành đủ mục tiêu."), false);
                sync.send(player);
                return;
            }
            if (!state.claim(quest.id())) {
                sync.send(player);
                return;
            }
            store.saveNow(player.getUuid());
            rewards.grant(player, quest);
            sync.send(player);
        } catch (Throwable t) {
            SVQuest.LOGGER.error("SVQuest claim failed safely for {} / {}", player.getName().getString(), questId, t);
            sync.send(player);
        }
    }

    public void adminAdd(ServerPlayerEntity player, String key, long amount) {
        mutate(player, state -> { state.adminAdd(normalize(key), amount); return true; }, "admin-add:" + key);
    }
    public void adminSet(ServerPlayerEntity player, String key, long value) {
        mutate(player, state -> { state.adminSet(normalize(key), value); return true; }, "admin-set:" + key);
    }
    public void reconcile(ServerPlayerEntity player) { mutate(player, QuestStateStore.PlayerState::reconcileClaimedProgress, "reconcile"); }

    private void mutate(ServerPlayerEntity player, Mutation operation, String source) {
        try {
            QuestStateStore.PlayerState state = store.get(player.getUuid());
            boolean changed = operation.apply(state);
            if (changed) store.saveNow(player.getUuid());
            sync.send(player);
        } catch (Throwable t) {
            SVQuest.LOGGER.error("SVQuest progression failed safely for {} ({})", player.getName().getString(), source, t);
        }
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
    }

    @FunctionalInterface private interface Mutation { boolean apply(QuestStateStore.PlayerState state); }
}
