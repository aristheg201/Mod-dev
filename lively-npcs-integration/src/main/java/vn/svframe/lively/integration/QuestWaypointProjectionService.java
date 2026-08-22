package vn.svframe.lively.integration;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.lively.actor.ActorId;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.quest.QuestRuntime;
import vn.svframe.lively.quest.QuestWaypointResolver;

import java.util.Comparator;
import java.util.UUID;

/** Keeps the player's current locatable Lively quest projected into the optional waypoint implementation. */
final class QuestWaypointProjectionService {
    private static final String KEY = "lively:quest";
    private volatile MinecraftServer server;
    private volatile boolean installed;

    private final QuestRuntime.Listener listener = new QuestRuntime.Listener() {
        @Override public void onClaimed(QuestRuntime.Quest quest) { refreshOwner(quest.owner(), quest); }
        @Override public void onProgressed(QuestRuntime.Quest before, QuestRuntime.Quest after) { refreshOwner(after.owner(), after); }
        @Override public void onStatusChanged(QuestRuntime.Quest before, QuestRuntime.Quest after) { refreshOwner(after.owner(), after); }
    };

    synchronized void install() {
        if (installed) return;
        installed = true;
        LivelyApi.quests().addListener(listener);
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            if (server == stopping) server = null;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> refresh(handler.player, null));
    }

    private void refreshOwner(ActorId owner, QuestRuntime.Quest preferred) {
        if (owner == null || owner.kind() != ActorId.Kind.PLAYER) return;
        MinecraftServer current = server;
        if (current == null) return;
        ServerPlayerEntity player = current.getPlayerManager().getPlayer(owner.uuid());
        if (player != null) refresh(player, preferred);
    }

    private void refresh(ServerPlayerEntity player, QuestRuntime.Quest preferred) {
        if (player == null || !LivelyApi.waypoints().available()) return;
        QuestRuntime.Quest selected = select(player.getUuid(), preferred);
        if (selected == null) {
            LivelyApi.waypoints().clear(player, KEY);
            return;
        }
        var target = QuestWaypointResolver.resolve(selected, LivelyApi.structures()).orElse(null);
        if (target == null) {
            selected = selectLocatable(player.getUuid(), selected.id());
            target = selected == null ? null : QuestWaypointResolver.resolve(selected, LivelyApi.structures()).orElse(null);
        }
        if (target == null) {
            LivelyApi.waypoints().clear(player, KEY);
            return;
        }
        LivelyApi.waypoints().show(player, KEY, target.world(), target.position(), target.label());
    }

    private QuestRuntime.Quest select(UUID playerId, QuestRuntime.Quest preferred) {
        if (preferred != null && preferred.owner() != null && preferred.owner().kind() == ActorId.Kind.PLAYER
                && preferred.owner().uuid().equals(playerId) && preferred.status() == QuestRuntime.Status.ACTIVE
                && QuestWaypointResolver.resolve(preferred, LivelyApi.structures()).isPresent()) return preferred;
        return selectLocatable(playerId, null);
    }

    private QuestRuntime.Quest selectLocatable(UUID playerId, UUID excluded) {
        ActorId owner = new ActorId(playerId, ActorId.Kind.PLAYER);
        return LivelyApi.quests().byOwner(owner).stream()
                .filter(quest -> quest.status() == QuestRuntime.Status.ACTIVE)
                .filter(quest -> excluded == null || !quest.id().equals(excluded))
                .filter(quest -> QuestWaypointResolver.resolve(quest, LivelyApi.structures()).isPresent())
                .sorted(Comparator.comparing(QuestRuntime.Quest::createdAt).reversed()
                        .thenComparing(quest -> quest.id().toString()))
                .findFirst().orElse(null);
    }
}
