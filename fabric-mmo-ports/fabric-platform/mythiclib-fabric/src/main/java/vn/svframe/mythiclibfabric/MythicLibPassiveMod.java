package vn.svframe.mythiclibfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.util.Map;
import java.util.UUID;

/** Fabric lifecycle adapter for the legacy MythicLib passive-skill runtime. */
public final class MythicLibPassiveMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> PassiveSkillRuntime.tick(MythicLibFabricMod.currentTick()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PassiveSkillRuntime.fire(handler.getPlayer().getUuid(), LegacyTriggerType.LOGIN,
                        handler.getPlayer().getUuid(), Map.of()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PassiveSkillRuntime.clear(handler.getPlayer().getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PassiveSkillRuntime.clearAll());
    }

    public static PassiveSkillRuntime.Binding register(UUID owner,
                                                       String key,
                                                       String trigger,
                                                       String skillId,
                                                       Map<String, ?> parameters,
                                                       long cooldownTicks,
                                                       long timerPeriodTicks) {
        return PassiveSkillRuntime.register(owner, key, LegacyTriggerType.parse(trigger), skillId,
                parameters, cooldownTicks, timerPeriodTicks);
    }

    public static int fire(UUID owner, String trigger, UUID target, Map<String, ?> context) {
        return PassiveSkillRuntime.fire(owner, LegacyTriggerType.parse(trigger), target, context);
    }

    public static boolean unregister(UUID owner, UUID bindingId) {
        return PassiveSkillRuntime.unregister(owner, bindingId);
    }

    public static int unregisterByKey(UUID owner, String key) {
        return PassiveSkillRuntime.unregisterByKey(owner, key);
    }
}
