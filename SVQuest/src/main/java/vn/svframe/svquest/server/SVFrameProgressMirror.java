package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svquest.SVQuest;

import java.util.Map;

/** Mirrors absolute SVFrameMMO progression so old players are not forced to repeat levels/skill ownership. */
public final class SVFrameProgressMirror {
    private final MinecraftServer server;
    private final QuestEngine engine;
    private int ticks;

    public SVFrameProgressMirror(MinecraftServer server, QuestEngine engine) {
        this.server = server;
        this.engine = engine;
    }

    public void tick() {
        if (++ticks % 100 != 0 || !FabricLoader.getInstance().isModLoaded("svframemmo")) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try { mirror(player); }
            catch (Throwable t) { SVQuest.LOGGER.debug("SVFrameMMO mirror failed safely for {}: {}", player.getName().getString(), t.toString()); }
        }
    }

    private void mirror(ServerPlayerEntity player) throws Exception {
        Class<?> root = Class.forName("vn.svframe.svframemmo.SVFrameMMO");
        Object manager = root.getMethod("playerData").invoke(null);
        Object data = manager.getClass().getMethod("get", ServerPlayerEntity.class).invoke(manager, player);
        if (data == null) return;
        long level = number(call(data, "getLevel"));
        engine.absolute(player, "TRAINER_LEVEL", level, Map.of("target", "trainer"));
        Object learned = call(data, "getSkillLevels");
        if (learned instanceof Map<?, ?> map) {
            engine.absolute(player, "POKESKILL_COUNT", map.size(), Map.of("target", "pokeskill"));
        }
    }

    private static Object call(Object target, String method) {
        if (target == null) return null;
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (Throwable ignored) { return null; }
    }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
}
