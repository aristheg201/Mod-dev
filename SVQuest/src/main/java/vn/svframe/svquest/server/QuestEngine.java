package vn.svframe.svquest.server;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.util.Locale;
import java.util.Map;

/** Server-authoritative engine for the full beta.5 prerequisite graph. */
public final class QuestEngine {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("starter", "STARTER_CHOSEN"),
            Map.entry("capture", "CAPTURE"),
            Map.entry("defeat", "DEFEAT"),
            Map.entry("battle_win", "BATTLE_WIN"),
            Map.entry("trainer_level", "TRAINER_LEVEL"),
            Map.entry("shop_purchase", "SHOP_BUY"),
            Map.entry("shop_sell", "SHOP_SELL"),
            Map.entry("evolve", "EVOLVE"),
            Map.entry("collect_egg", "BREED_EGG"),
            Map.entry("hatch", "HATCH"),
            Map.entry("skill_purchase", "POKESKILL_PURCHASE"),
            Map.entry("skill_count", "POKESKILL_COUNT"),
            Map.entry("gts_listing", "GTS_LIST"),
            Map.entry("gts_trade", "GTS_PURCHASE"),
            Map.entry("wonder_trade", "WONDERTRADE"),
            Map.entry("sts_trade", "STS_SELL"),
            Map.entry("hunt_complete", "HUNT_COMPLETE"),
            Map.entry("raid_complete", "RAID_WIN"),
            Map.entry("ranked_win", "RANKED_WIN"),
            Map.entry("battle_tower_win", "TOWER_WIN"),
            Map.entry("factory_complete", "FACTORY_RUN_COMPLETE"),
            Map.entry("expedition_complete", "EXPEDITION_COMPLETE"),
            Map.entry("showcase_complete", "SHOWCASE_PLACE"),
            Map.entry("skin_purchase", "SKIN_PURCHASE"),
            Map.entry("mega_use", "MEGA_EVOLUTION"),
            Map.entry("tera_use", "TERASTALLIZE"),
            Map.entry("fusion_dance", "FUSION_DANCE"),
            Map.entry("fusion_complete", "FUSION_POTARA")
    );

    private final QuestStateStore store;
    private final RewardDispatcher rewards;
    private StateSync sync = player -> {};

    @FunctionalInterface public interface StateSync { void send(ServerPlayerEntity player); }

    public QuestEngine(QuestStateStore store, RewardDispatcher rewards) {
        this.store = store;
        this.rewards = rewards;
    }

    public void setSync(StateSync sync) { this.sync = sync == null ? player -> {} : sync; }

    public void signal(ServerPlayerEntity player, String key) { signal(player, key, 1); }
    public void signal(ServerPlayerEntity player, String key, long amount) { emit(player, alias(key), amount, Map.of()); }
    public void metric(ServerPlayerEntity player, String key, long value) { emit(player, alias(key), value, Map.of()); }

    public void emit(ServerPlayerEntity player, String type) { emit(player, type, 1, Map.of()); }
    public void emit(ServerPlayerEntity player, String type, long amount) { emit(player, type, amount, Map.of()); }

    /** Metadata supports beta.5 target/metaKey objectives such as Research species and crate ids. */
    public void emit(ServerPlayerEntity player, String type, long amount, Map<String, String> meta) {
        if (player == null || type == null || type.isBlank()) return;
        mutate(player, state -> state.emit(alias(type), amount, meta == null ? Map.of() : meta), "event:" + type);
    }

    public void adminAdd(ServerPlayerEntity player, String key, long amount) {
        mutate(player, state -> { state.adminAdd(key, amount); return true; }, "admin-add:" + key);
    }

    public void adminSet(ServerPlayerEntity player, String key, long value) {
        mutate(player, state -> { state.adminSet(key, value); return true; }, "admin-set:" + key);
    }

    /** Used after join/import so already-complete beta.5 state can resume safely. */
    public void reconcile(ServerPlayerEntity player) {
        mutate(player, state -> false, "reconcile");
    }

    private void mutate(ServerPlayerEntity player, Mutation operation, String source) {
        try {
            QuestStateStore.PlayerState state = store.get(player.getUuid());
            boolean changed = operation.apply(state);
            boolean claimedAny = claimCompleted(player, state);
            if (changed || claimedAny) store.saveNow(player.getUuid());
            sync.send(player);
        } catch (Throwable t) {
            SVQuest.LOGGER.error("SVQuest progression failed safely for {} ({})", player.getName().getString(), source, t);
        }
    }

    private boolean claimCompleted(ServerPlayerEntity player, QuestStateStore.PlayerState state) {
        boolean any = false;
        // Claim before paying rewards, so a reward-side exception can never duplicate a quest.
        for (int guard = 0; guard < QuestCatalog.QUESTS.size(); guard++) {
            var completed = state.completeUnclaimed();
            if (completed.isEmpty()) break;
            boolean round = false;
            for (QuestCatalog.Quest quest : completed) {
                if (!state.claim(quest.id())) continue;
                round = true;
                any = true;
                rewards.grant(player, quest);
            }
            if (!round) break;
        }
        return any;
    }

    private static String alias(String key) {
        if (key == null) return "";
        String raw = key.trim();
        String mapped = ALIASES.get(raw.toLowerCase(Locale.ROOT));
        return mapped == null ? raw.toUpperCase(Locale.ROOT) : mapped;
    }

    @FunctionalInterface private interface Mutation { boolean apply(QuestStateStore.PlayerState state); }
}
