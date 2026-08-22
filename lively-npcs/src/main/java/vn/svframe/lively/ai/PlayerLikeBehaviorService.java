package vn.svframe.lively.ai;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.navigation.WorldNavigationService;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcRuntime;
import vn.svframe.lively.persistence.NpcStateRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gives spawned NPCs the boring-but-important physical behaviour players expect to see:
 * looking around, wandering, reacting to nearby players and retaliating when attacked.
 * This layer never starts dialogue by itself. Talking remains interaction-driven.
 */
public final class PlayerLikeBehaviorService {
    private static final int AMBIENT_BUDGET = 24;
    private static final double AGGRO_CHASE_RANGE_SQ = 24D * 24D;
    private static final double MELEE_RANGE_SQ = 3.25D * 3.25D;
    private static final long AGGRO_MEMORY_TICKS = 240L;
    private static final long ATTACK_COOLDOWN_TICKS = 12L;

    private record Aggro(UUID playerId, long expiresAt) {}

    private final NpcRuntime npcs;
    private final NpcStateRegistry states;
    private final WorldNavigationService navigation;
    private final ConcurrentHashMap<UUID, Aggro> aggro = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> nextAttack = new ConcurrentHashMap<>();
    private int ambientCursor;

    public PlayerLikeBehaviorService(NpcRuntime npcs, NpcStateRegistry states, WorldNavigationService navigation) {
        this.npcs = npcs;
        this.states = states;
        this.navigation = navigation;
    }

    public void onPlayerAttack(ServerPlayerEntity player, UUID entityUuid) {
        UUID npcId = npcs.npcForEntity(entityUuid).orElse(null);
        if (npcId == null) return;
        long now = player.getServer().getTicks();
        aggro.put(npcId, new Aggro(player.getUuid(), now + AGGRO_MEMORY_TICKS));
        states.get(npcId).ifPresent(state -> state.remember("attacked_by_player",
                Map.of("player", player.getUuid().toString()), .92D, 1D));
        if (LivelyApi.animations() != null) LivelyApi.animations().play(player.getServer(), npcId, "hurt");
    }

    public void tick(MinecraftServer server, long tick) {
        combatPulse(server, tick);
        if (tick % 40L == 0L) ambientPulse(server, tick);
        if (tick % 200L == 0L) {
            aggro.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= tick);
            nextAttack.keySet().removeIf(id -> npcs.get(id).isEmpty());
        }
    }

    private void combatPulse(MinecraftServer server, long tick) {
        for (var entry : List.copyOf(aggro.entrySet())) {
            UUID npcId = entry.getKey();
            Aggro threat = entry.getValue();
            if (threat.expiresAt() <= tick) { aggro.remove(npcId, threat); continue; }
            NpcDefinition definition = npcs.get(npcId).orElse(null);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(threat.playerId());
            Vec3d position = npcs.position(npcId).orElse(null);
            String world = npcs.worldKey(npcId).orElse(null);
            if (definition == null || !definition.spawned() || !definition.aiEnabled() || target == null || position == null || world == null) {
                aggro.remove(npcId, threat);
                navigation.stop(npcId);
                continue;
            }
            if (!target.getServerWorld().getRegistryKey().getValue().toString().equals(world)) {
                aggro.remove(npcId, threat);
                navigation.stop(npcId);
                continue;
            }
            double distanceSq = target.getPos().squaredDistanceTo(position);
            if (distanceSq > AGGRO_CHASE_RANGE_SQ) {
                aggro.remove(npcId, threat);
                navigation.stop(npcId);
                continue;
            }

            npcs.lookAt(server, npcId, target.getEyePos());
            if (distanceSq <= MELEE_RANGE_SQ) {
                navigation.stop(npcId);
                if (nextAttack.getOrDefault(npcId, 0L) <= tick) {
                    if (LivelyApi.animations() != null) LivelyApi.animations().play(server, npcId, "attack");
                    if (npcs.attack(server, npcId, target.getUuid())) {
                        nextAttack.put(npcId, tick + ATTACK_COOLDOWN_TICKS);
                        states.get(npcId).ifPresent(state -> state.remember("retaliated_against_player",
                                Map.of("player", target.getUuid().toString()), .55D, 1D));
                    }
                }
            } else if (navigation.status(npcId).isEmpty()) {
                navigation.follow(npcId, target.getUuid());
                if (LivelyApi.animations() != null) LivelyApi.animations().play(server, npcId, "run");
            }
        }
    }

    private void ambientPulse(MinecraftServer server, long tick) {
        List<NpcDefinition> active = npcs.snapshot().values().stream()
                .filter(NpcDefinition::spawned)
                .filter(NpcDefinition::aiEnabled)
                .sorted(Comparator.comparing(d -> d.id().toString()))
                .toList();
        if (active.isEmpty()) return;

        int count = Math.min(AMBIENT_BUDGET, active.size());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            NpcDefinition d = active.get((ambientCursor + i) % active.size());
            if (aggro.containsKey(d.id()) || navigation.status(d.id()).isPresent()) continue;
            if (Boolean.parseBoolean(d.metadata().getOrDefault("behavior.stationary", "false"))) continue;

            Vec3d position = npcs.position(d.id()).orElse(null);
            String world = npcs.worldKey(d.id()).orElse(d.world());
            if (position == null || world == null) continue;

            ServerPlayerEntity nearby = server.getPlayerManager().getPlayerList().stream()
                    .filter(player -> player.getServerWorld().getRegistryKey().getValue().toString().equals(world))
                    .filter(player -> player.getPos().squaredDistanceTo(position) <= 12D * 12D)
                    .min(Comparator.comparingDouble(player -> player.getPos().squaredDistanceTo(position)))
                    .orElse(null);

            double roll = random.nextDouble();
            if (nearby != null && roll < .30D) {
                npcs.lookAt(server, d.id(), nearby.getEyePos());
                continue;
            }
            if (roll < .72D) {
                double angle = random.nextDouble(0D, Math.PI * 2D);
                double radius = random.nextDouble(4D, 12D);
                Vec3d target = position.add(Math.cos(angle) * radius, 0D, Math.sin(angle) * radius);
                if (navigation.goTo(d.id(), world, target) && LivelyApi.animations() != null) {
                    LivelyApi.animations().play(server, d.id(), random.nextDouble() < .16D ? "run" : "walk");
                }
            } else if (LivelyApi.animations() != null) {
                LivelyApi.animations().play(server, d.id(), "stand");
            }
        }
        ambientCursor = (ambientCursor + count) % active.size();
    }
}
