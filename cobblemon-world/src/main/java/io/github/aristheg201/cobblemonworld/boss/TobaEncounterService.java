package io.github.aristheg201.cobblemonworld.boss;

import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TobaEncounterService {
    private static final Map<UUID, TobaBossState> STATES = new ConcurrentHashMap<>();
    private TobaEncounterService() {}

    public static TobaBossState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), ignored -> eligible(player) ? TobaBossState.READY : TobaBossState.LOCKED);
    }

    public static boolean eligible(ServerPlayer player) {
        PlayerProgression p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        return p.storyFlags.contains("main_story_complete") && !p.storyFlags.contains("toba_defeated");
    }

    public static void beginPhaseOne(ServerPlayer player) {
        if (state(player) != TobaBossState.READY) throw new IllegalStateException("TOBA encounter is not ready");
        STATES.put(player.getUUID(), TobaBossState.PHASE_ONE_COBBLEMON);
    }

    public static void cobblemonPhaseWon(ServerPlayer player) {
        if (state(player) != TobaBossState.PHASE_ONE_COBBLEMON) return;
        STATES.put(player.getUUID(), TobaBossState.TRANSFORMING);
    }

    public static void beginRpgPhase(ServerPlayer player) {
        if (state(player) == TobaBossState.TRANSFORMING) STATES.put(player.getUUID(), TobaBossState.PHASE_TWO_RPG);
    }

    public static void defeat(ServerPlayer player) {
        if (state(player) != TobaBossState.PHASE_TWO_RPG) return;
        STATES.put(player.getUUID(), TobaBossState.DEFEATED);
        ProgressionStore.INSTANCE.getOrCreate(player.getUUID()).storyFlags.add("toba_defeated");
        ProgressionStore.INSTANCE.save();
    }
}
