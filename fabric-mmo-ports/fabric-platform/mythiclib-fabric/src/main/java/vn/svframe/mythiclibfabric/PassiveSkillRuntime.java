package vn.svframe.mythiclibfabric;

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
    private static final Map<UUID, CopyOnWriteArrayList<Binding>> BY_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CAST = new ConcurrentHashMap<>();
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
        boolean removed = list.removeIf(binding -> binding.id().equals(bindingId));
        if (removed) LAST_CAST.remove(bindingId);
        if (list.isEmpty()) BY_PLAYER.remove(owner, list);
        return removed;
    }

    public static int unregisterByKey(UUID owner, String key) {
        CopyOnWriteArrayList<Binding> list = BY_PLAYER.get(owner);
        if (list == null || key == null) return 0;
        int before = list.size();
        List<UUID> removedIds = new ArrayList<>();
        list.removeIf(binding -> {
            boolean remove = binding.key().equals(key);
            if (remove) removedIds.add(binding.id());
            return remove;
        });
        removedIds.forEach(LAST_CAST::remove);
        if (list.isEmpty()) BY_PLAYER.remove(owner, list);
        return before - list.size();
    }

    public static void clear(UUID owner) {
        Collection<Binding> removed = BY_PLAYER.remove(owner);
        if (removed != null) removed.forEach(binding -> LAST_CAST.remove(binding.id()));
    }

    public static void clearAll() {
        BY_PLAYER.clear();
        LAST_CAST.clear();
    }

    public static List<Binding> bindings(UUID owner) {
        List<Binding> list = BY_PLAYER.get(owner);
        return list == null ? List.of() : List.copyOf(list);
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
        for (Map.Entry<UUID, CopyOnWriteArrayList<Binding>> entry : BY_PLAYER.entrySet()) {
            UUID owner = entry.getKey();
            for (Binding binding : entry.getValue()) {
                if (binding.trigger() != LegacyTriggerType.TIMER) continue;
                long last = LAST_CAST.getOrDefault(binding.id(), binding.registeredTick());
                if (now - last < binding.timerPeriodTicks()) continue;
                if (ready(binding, now) && MythicLibFabricMod.castSkill(binding.skillId(), owner, owner,
                        Map.of("trigger", LegacyTriggerType.TIMER.name(), "passive-key", binding.key()))) {
                    LAST_CAST.put(binding.id(), now);
                }
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
        LAST_CAST.remove(bindingId);
    }

    public static void reduceCooldown(UUID bindingId, long ticks) {
        if (ticks <= 0) return;
        LAST_CAST.computeIfPresent(bindingId, (ignored, last) -> last - ticks);
    }

    private static boolean ready(Binding binding, long now) {
        if (binding.cooldownTicks() <= 0) return true;
        Long last = LAST_CAST.get(binding.id());
        return last == null || now - last >= binding.cooldownTicks();
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
