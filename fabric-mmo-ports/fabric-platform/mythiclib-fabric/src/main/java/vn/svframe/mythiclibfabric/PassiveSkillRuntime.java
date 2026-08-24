package vn.svframe.mythiclibfabric;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-authoritative passive-skill registry and trigger dispatcher.
 *
 * This replaces the Bukkit PassiveSkillMap/SkillTriggers pairing. It is kept
 * independent from Fabric event classes on purpose: Fabric listeners, MMOItems,
 * MMOCore and compatibility modules all feed the same trigger surface.
 */
public final class PassiveSkillRuntime {
    private record TimerKey(UUID owner, String skillId) {}

    private static final Map<UUID, CopyOnWriteArrayList<Binding>> BY_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CAST = new ConcurrentHashMap<>();
    private static final Map<TimerKey, Long> TIMER_LAST_CAST = new ConcurrentHashMap<>();
    private static final AtomicLong REGISTRATION_SEQUENCE = new AtomicLong();

    private PassiveSkillRuntime() {}

    public static Binding register(UUID owner,
                                   String key,
                                   LegacyTriggerType trigger,
                                   String skillId,
                                   Map<String, ?> parameters,
                                   long cooldownTicks,
                                   long timerPeriodTicks) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(trigger, "trigger");
        if (skillId == null || skillId.isBlank()) throw new IllegalArgumentException("skillId cannot be blank");

        Binding binding = new Binding(
                UUID.randomUUID(),
                owner,
                key == null ? "passive-" + REGISTRATION_SEQUENCE.incrementAndGet() : key,
                trigger,
                skillId,
                immutableCopy(parameters),
                Math.max(0L, cooldownTicks),
                Math.max(1L, timerPeriodTicks),
                MythicLibFabricMod.currentTick());
        BY_PLAYER.computeIfAbsent(owner, ignored -> new CopyOnWriteArrayList<>()).add(binding);
        return binding;
    }

    public static boolean unregister(UUID owner, UUID bindingId) {
        CopyOnWriteArrayList<Binding> list = BY_PLAYER.get(owner);
        if (list == null) return false;
        List<Binding> removedBindings = new ArrayList<>();
        boolean removed = list.removeIf(binding -> {
            boolean match = binding.id().equals(bindingId);
            if (match) removedBindings.add(binding);
            return match;
        });
        for (Binding binding : removedBindings) {
            LAST_CAST.remove(binding.id());
            removeTimerKeyIfUnused(owner, binding.skillId(), list);
        }
        if (list.isEmpty()) BY_PLAYER.remove(owner, list);
        return removed;
    }

    public static int unregisterByKey(UUID owner, String key) {
        CopyOnWriteArrayList<Binding> list = BY_PLAYER.get(owner);
        if (list == null || key == null) return 0;
        int before = list.size();
        List<Binding> removedBindings = new ArrayList<>();
        list.removeIf(binding -> {
            boolean remove = binding.key().equals(key);
            if (remove) removedBindings.add(binding);
            return remove;
        });
        for (Binding binding : removedBindings) {
            LAST_CAST.remove(binding.id());
            removeTimerKeyIfUnused(owner, binding.skillId(), list);
        }
        if (list.isEmpty()) BY_PLAYER.remove(owner, list);
        return before - list.size();
    }

    public static void clear(UUID owner) {
        Collection<Binding> removed = BY_PLAYER.remove(owner);
        if (removed != null) removed.forEach(binding -> LAST_CAST.remove(binding.id()));
        TIMER_LAST_CAST.keySet().removeIf(key -> key.owner().equals(owner));
    }

    public static void clearAll() {
        BY_PLAYER.clear();
        LAST_CAST.clear();
        TIMER_LAST_CAST.clear();
    }

    public static List<Binding> bindings(UUID owner) {
        List<Binding> list = BY_PLAYER.get(owner);
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * Captures the effective passive bindings at projectile launch time.
     * The immutable binding records deliberately survive later equipment changes,
     * matching upstream ProjectileMetadata behavior.
     */
    public static Snapshot snapshot(UUID owner) {
        return new Snapshot(owner, bindings(owner));
    }

    public static int bindingCount() {
        int count = 0;
        for (List<Binding> bindings : BY_PLAYER.values()) count += bindings.size();
        return count;
    }

    public static int fire(UUID owner,
                           LegacyTriggerType trigger,
                           UUID target,
                           Map<String, ?> context) {
        List<Binding> bindings = BY_PLAYER.get(owner);
        if (bindings == null || bindings.isEmpty()) return 0;
        return fireBindings(owner, bindings, trigger, target, context);
    }

    /** Fires against launch-time bindings rather than the player's current equipment. */
    public static int fireSnapshot(Snapshot snapshot,
                                   LegacyTriggerType trigger,
                                   UUID target,
                                   Map<String, ?> context) {
        if (snapshot == null || snapshot.bindings().isEmpty()) return 0;
        return fireBindings(snapshot.owner(), snapshot.bindings(), trigger, target, context);
    }

    private static int fireBindings(UUID owner,
                                    List<Binding> bindings,
                                    LegacyTriggerType trigger,
                                    UUID target,
                                    Map<String, ?> context) {
        long now = MythicLibFabricMod.currentTick();
        int cast = 0;
        for (Binding binding : bindings) {
            if (binding.trigger() != trigger) continue;
            if (!ready(binding, now)) continue;

            Map<String, Object> merged = new LinkedHashMap<>(binding.parameters());
            if (context != null) merged.putAll(context);
            merged.putIfAbsent("trigger", trigger.name());
            merged.putIfAbsent("passive-key", binding.key());

            if (MythicLibFabricMod.castSkill(binding.skillId(), owner, target == null ? owner : target, merged)) {
                LAST_CAST.put(binding.id(), now);
                cast++;
            }
        }
        return cast;
    }

    /** Called once per dedicated/integrated server tick. */
    public static void tick(long now) {
        MinecraftServer server = MythicLibFabricMod.server();
        for (Map.Entry<UUID, CopyOnWriteArrayList<Binding>> entry : BY_PLAYER.entrySet()) {
            UUID owner = entry.getKey();
            if (server != null) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
                if (player == null || player.isSpectator()) continue;
            }

            for (Binding binding : entry.getValue()) {
                if (binding.trigger() != LegacyTriggerType.TIMER) continue;

                TimerKey key = new TimerKey(owner, binding.skillId());
                long last = TIMER_LAST_CAST.getOrDefault(key, now - binding.timerPeriodTicks());
                if (now - last < binding.timerPeriodTicks()) continue;
                if (!ready(binding, now)) continue;

                // Upstream MythicLib records the timer cast before executing it.
                // This prevents a failed or re-entrant skill from hammering every tick.
                TIMER_LAST_CAST.put(key, now);
                LAST_CAST.put(binding.id(), now);
                MythicLibFabricMod.castSkill(binding.skillId(), owner, owner,
                        Map.of("trigger", LegacyTriggerType.TIMER.name(), "passive-key", binding.key()));
            }
        }
    }

    public static long remainingCooldown(UUID bindingId) {
        Binding binding = find(bindingId);
        if (binding == null) return 0L;
        long last = LAST_CAST.getOrDefault(binding.id(), Long.MIN_VALUE / 4);
        return Math.max(0L, binding.cooldownTicks() - (MythicLibFabricMod.currentTick() - last));
    }

    public static void resetCooldown(UUID bindingId) {
        Binding binding = find(bindingId);
        LAST_CAST.remove(bindingId);
        if (binding != null && binding.trigger() == LegacyTriggerType.TIMER) {
            TIMER_LAST_CAST.remove(new TimerKey(binding.owner(), binding.skillId()));
        }
    }

    public static void reduceCooldown(UUID bindingId, long ticks) {
        if (ticks <= 0) return;
        LAST_CAST.computeIfPresent(bindingId, (ignored, last) -> last - ticks);
        Binding binding = find(bindingId);
        if (binding != null && binding.trigger() == LegacyTriggerType.TIMER) {
            TIMER_LAST_CAST.computeIfPresent(new TimerKey(binding.owner(), binding.skillId()), (ignored, last) -> last - ticks);
        }
    }

    private static boolean ready(Binding binding, long now) {
        if (binding.cooldownTicks() <= 0) return true;
        Long last = LAST_CAST.get(binding.id());
        return last == null || now - last >= binding.cooldownTicks();
    }

    private static void removeTimerKeyIfUnused(UUID owner, String skillId, List<Binding> remaining) {
        for (Binding binding : remaining) {
            if (binding.trigger() == LegacyTriggerType.TIMER && binding.skillId().equals(skillId)) return;
        }
        TIMER_LAST_CAST.remove(new TimerKey(owner, skillId));
    }

    private static Binding find(UUID id) {
        if (id == null) return null;
        for (List<Binding> bindings : BY_PLAYER.values()) {
            for (Binding binding : bindings) if (binding.id().equals(id)) return binding;
        }
        return null;
    }

    private static Map<String, Object> immutableCopy(Map<String, ?> parameters) {
        if (parameters == null || parameters.isEmpty()) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : parameters.entrySet()) copy.put(entry.getKey(), entry.getValue());
        return Collections.unmodifiableMap(copy);
    }

    public record Snapshot(UUID owner, List<Binding> bindings) {
        public Snapshot {
            Objects.requireNonNull(owner, "owner");
            bindings = bindings == null ? List.of() : List.copyOf(bindings);
        }
    }

    public record Binding(UUID id,
                          UUID owner,
                          String key,
                          LegacyTriggerType trigger,
                          String skillId,
                          Map<String, Object> parameters,
                          long cooldownTicks,
                          long timerPeriodTicks,
                          long registeredTick) {}
}
