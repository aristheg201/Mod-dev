package vn.svframe.lively.quest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.economy.EconomyEngine;
import vn.svframe.lively.world.SemanticStructureRegistry;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-session quest side effects. Quest state stays generic; this service owns optional navigation projection
 * and idempotent virtual rewards without touching player inventories or arbitrary commands.
 */
public final class QuestLifecycleService implements AutoCloseable {
    private static final ActorId REWARD_TREASURY = new ActorId(new UUID(0L, 0x51554553544CL), ActorId.Kind.SYSTEM);
    private static final long TREASURY_BALANCE = 10_000_000_000_000L;
    private static final long MAX_REWARD = 1_000_000_000L;

    private final MinecraftServer server;
    private final QuestRuntime.Listener listener = new QuestRuntime.Listener() {
        @Override public void onClaimed(QuestRuntime.Quest quest) { refreshWaypoint(quest); }
        @Override public void onProgressed(QuestRuntime.Quest before, QuestRuntime.Quest after) {
            if (after.status() == QuestRuntime.Status.ACTIVE) refreshWaypoint(after);
        }
        @Override public void onStatusChanged(QuestRuntime.Quest before, QuestRuntime.Quest after) {
            if (after.status() != QuestRuntime.Status.ACTIVE) clearWaypoint(after);
        }
        @Override public void onCompleted(QuestRuntime.Quest quest) { payReward(quest, true); }
    };

    public QuestLifecycleService(MinecraftServer server) {
        this.server = server;
        LivelyApi.economy().ensureWallet(REWARD_TREASURY, TREASURY_BALANCE);
        LivelyApi.quests().addListener(listener);
        reconcileCompletedRewards();
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        ActorId owner = new ActorId(player.getUuid(), ActorId.Kind.PLAYER);
        for (QuestRuntime.Quest quest : LivelyApi.quests().byOwner(owner)) {
            if (quest.status() == QuestRuntime.Status.ACTIVE) refreshWaypoint(quest);
        }
    }

    private void reconcileCompletedRewards() {
        for (QuestRuntime.Quest quest : LivelyApi.quests().snapshot().quests().values()) {
            if (quest.status() == QuestRuntime.Status.COMPLETED && !quest.facts().containsKey("reward_paid")) payReward(quest, false);
        }
    }

    private void payReward(QuestRuntime.Quest quest, boolean notify) {
        if (quest.owner() == null || quest.facts().containsKey("reward_paid")) return;
        long amount = positiveLong(quest.facts().get("reward_budget"), 0L, MAX_REWARD);
        if (amount <= 0L) {
            LivelyApi.quests().markFactIfAbsent(quest.id(), "reward_paid", "none");
            return;
        }
        String reference = "quest:" + quest.id() + ":reward";
        var transaction = LivelyApi.economy().transferOnce(EconomyEngine.TransactionType.GIFT,
                REWARD_TREASURY, quest.owner(), amount, reference);
        if (transaction.isEmpty()) return;
        LivelyApi.quests().markFactIfAbsent(quest.id(), "reward_paid", "virtual:" + transaction.get().id());

        if (notify && quest.owner().kind() == ActorId.Kind.PLAYER) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(quest.owner().uuid());
            if (player != null) player.sendMessage(Text.literal("[Lively] Hoàn thành: " + quest.title() + " • thưởng " + amount + " credit."), false);
        }
    }

    private void refreshWaypoint(QuestRuntime.Quest quest) {
        if (!LivelyApi.waypoints().available() || quest.owner() == null || quest.owner().kind() != ActorId.Kind.PLAYER) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(quest.owner().uuid());
        if (player == null) return;
        QuestRuntime.Objective objective = quest.objectives().stream()
                .filter(value -> !value.hidden())
                .filter(value -> quest.progress().getOrDefault(value.id(), 0L) < value.required())
                .sorted(Comparator.comparing(QuestRuntime.Objective::optional))
                .findFirst().orElse(null);
        if (objective == null) {
            clearWaypoint(quest);
            return;
        }
        Destination destination = destination(objective);
        if (destination == null) return;
        LivelyApi.waypoints().show(player, waypointKey(quest.id()), destination.world(), destination.position(),
                quest.title() + " • " + objective.id());
    }

    private Destination destination(QuestRuntime.Objective objective) {
        String structureId = objective.facts().getOrDefault("structure", objective.target());
        SemanticStructureRegistry.Structure structure = LivelyApi.structures().get(structureId).orElse(null);
        if (structure != null) {
            Vec3d point = point(structure.points().get("quest"));
            if (point == null) point = point(structure.points().get("entrance"));
            if (point == null) {
                var b = structure.bounds();
                point = new Vec3d((b.minX() + b.maxX() + 1D) / 2D, b.minY() + 1D, (b.minZ() + b.maxZ() + 1D) / 2D);
            }
            return new Destination(structure.bounds().world(), point);
        }
        String world = objective.facts().get("world");
        Vec3d direct = coordinates(objective.facts());
        return world == null || direct == null ? null : new Destination(world, direct);
    }

    private void clearWaypoint(QuestRuntime.Quest quest) {
        if (!LivelyApi.waypoints().available() || quest.owner() == null || quest.owner().kind() != ActorId.Kind.PLAYER) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(quest.owner().uuid());
        if (player != null) LivelyApi.waypoints().clear(player, waypointKey(quest.id()));
    }

    private static Vec3d coordinates(Map<String, String> facts) {
        try {
            String x = facts.get("x"), y = facts.get("y"), z = facts.get("z");
            return x == null || y == null || z == null ? null : new Vec3d(Double.parseDouble(x), Double.parseDouble(y), Double.parseDouble(z));
        } catch (NumberFormatException ignored) { return null; }
    }

    private static Vec3d point(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(",");
        if (parts.length < 3) return null;
        try { return new Vec3d(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim())); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static long positiveLong(String raw, long fallback, long max) {
        try { return Math.max(0L, Math.min(max, Long.parseLong(raw == null ? Long.toString(fallback) : raw.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static String waypointKey(UUID id) { return "lively_quest_" + id; }
    private record Destination(String world, Vec3d position) {}

    @Override public void close() { LivelyApi.quests().removeListener(listener); }
}
