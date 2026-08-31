package vn.svframe.svquest.server;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

/** Central server-authoritative progression engine. */
public final class QuestEngine {
    private final QuestStateStore store;
    private final RewardDispatcher rewards;
    private final Runnable noop = () -> {};
    private StateSync sync = player -> {};

    @FunctionalInterface
    public interface StateSync { void send(ServerPlayerEntity player); }

    public QuestEngine(QuestStateStore store, RewardDispatcher rewards) {
        this.store = store;
        this.rewards = rewards;
    }

    public void setSync(StateSync sync) {
        this.sync = sync == null ? player -> {} : sync;
    }

    public void signal(ServerPlayerEntity player, String key) { signal(player, key, 1); }

    public void signal(ServerPlayerEntity player, String key, int amount) {
        if (player == null || key == null || key.isBlank() || amount <= 0) return;
        mutate(player, state -> state.signal(key, amount), "signal:" + key);
    }

    public void metric(ServerPlayerEntity player, String key, int value) {
        if (player == null || key == null || key.isBlank()) return;
        mutate(player, state -> state.metric(key, value), "metric:" + key);
    }

    public void adminAdd(ServerPlayerEntity player, String key, int amount) {
        mutate(player, state -> state.add(key, amount), "admin-add:" + key);
    }

    public void adminSet(ServerPlayerEntity player, String key, int value) {
        mutate(player, state -> state.set(key, value), "admin-set:" + key);
    }

    private void mutate(ServerPlayerEntity player, java.util.function.Consumer<QuestStateStore.PlayerState> operation, String source) {
        try {
            QuestStateStore.PlayerState state = store.get(player.getUuid());
            int before = state.questIndex();
            operation.accept(state);
            int after = state.questIndex();
            if (after > before) {
                for (int i = before; i < after; i++) {
                    QuestCatalog.Quest completed = QuestCatalog.byIndex(i);
                    if (state.markRewarded(completed.id())) rewards.grant(player, completed);
                }
                store.saveNow(player.getUuid());
            }
            sync.send(player);
        } catch (Throwable t) {
            SVQuest.LOGGER.error("SVQuest progression mutation failed safely for {} ({})", player.getName().getString(), source, t);
        }
    }
}
