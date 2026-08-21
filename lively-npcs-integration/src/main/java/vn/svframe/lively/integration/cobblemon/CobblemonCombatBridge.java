package vn.svframe.lively.integration.cobblemon;

import vn.svframe.lively.api.CombatAdapter;
import vn.svframe.lively.combat.CombatCortex;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reflection-isolated first bridge for Cobblemon battle handles.
 * This keeps Cobblemon classes out of the core JAR while the typed adapter surface stabilizes.
 * Integration-specific direct API bindings can replace individual probes without changing CombatCortex.
 */
public final class CobblemonCombatBridge implements CombatAdapter {
    @Override public String id() { return "cobblemon"; }

    @Override
    public boolean supports(Object battleHandle) {
        if (battleHandle == null) return false;
        String name = battleHandle.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("cobblemon") && name.contains("battle");
    }

    @Override
    public CombatCortex.CombatState snapshot(Object battleHandle, UUID npcId) {
        List<CombatCortex.CombatAction> actions = discoverLegalActions(battleHandle);
        long revision = probeLong(battleHandle, "getTurn", "turn", "getTurnNumber").orElse(0L);
        return new CombatCortex.CombatState(
                revision,
                (int) Math.min(Integer.MAX_VALUE, Math.max(0L, revision)),
                0.65D,
                0.45D,
                actions,
                Map.of("adapter.cobblemon", 1D));
    }

    @Override
    public CombatCortex.Simulator simulator(Object battleHandle, UUID npcId) {
        // Conservative until direct Cobblemon damage/state prediction bindings are installed:
        // never invent hidden information; evaluate only metadata exposed by legal actions.
        return (state, action) -> List.of(new CombatCortex.Outcome(
                action.immediateValue() * 0.35D,
                1D,
                null));
    }

    @Override
    public void apply(Object battleHandle, UUID npcId, CombatCortex.Decision decision) {
        // Intentionally no reflective mutation. Applying a battle action must use a verified,
        // version-specific Cobblemon binding rather than guessing a method and risking corruption.
        throw new UnsupportedOperationException("Cobblemon action application binding is not installed yet");
    }

    private List<CombatCortex.CombatAction> discoverLegalActions(Object handle) {
        Object value = invokeZeroArg(handle, "getLegalActions", "legalActions", "getActions");
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<CombatCortex.CombatAction> result = new ArrayList<>();
        int index = 0;
        for (Object candidate : iterable) {
            if (candidate == null || index >= 64) break;
            String id = String.valueOf(candidate);
            result.add(new CombatCortex.CombatAction(id, 0.25D, 0.15D, Map.of("source", "cobblemon")));
            index++;
        }
        return List.copyOf(result);
    }

    private static java.util.OptionalLong probeLong(Object target, String... methods) {
        Object value = invokeZeroArg(target, methods);
        return value instanceof Number number ? java.util.OptionalLong.of(number.longValue()) : java.util.OptionalLong.empty();
    }

    private static Object invokeZeroArg(Object target, String... names) {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() == 0) return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Probe only. No mutation and no failure escalation.
            }
        }
        return null;
    }
}
