package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svquest.SVQuest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** CobblePass + CobbleCalendar progress detection using their production public APIs through reflection. */
public final class SeasonProgressPoller {
    private final MinecraftServer server;
    private final QuestEngine engine;
    private final Map<UUID, PassState> pass = new HashMap<>();
    private final Map<UUID, Long> calendar = new HashMap<>();
    private int ticks;

    private record PassState(int level, int xp) {}

    public SeasonProgressPoller(MinecraftServer server, QuestEngine engine) {
        this.server = server;
        this.engine = engine;
    }

    public void tick() {
        if (++ticks % 40 != 0) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try { pollBattlePass(player); }
            catch (Throwable t) { SVQuest.LOGGER.debug("CobblePass progression probe failed safely: {}", t.toString()); }
            try { pollCalendar(player); }
            catch (Throwable t) { SVQuest.LOGGER.debug("CobbleCalendar progression probe failed safely: {}", t.toString()); }
        }
    }

    public void onJoin(ServerPlayerEntity player) {
        pass.remove(player.getUuid());
        calendar.remove(player.getUuid());
    }

    public void onQuit(ServerPlayerEntity player) {
        pass.remove(player.getUuid());
        calendar.remove(player.getUuid());
    }

    private void pollBattlePass(ServerPlayerEntity player) throws Exception {
        if (!FabricLoader.getInstance().isModLoaded("cobblepass_fabric")) return;
        Class<?> root = Class.forName("com.cobblemon.mdks.cobblepass.CobblePass");
        Object battlePass = root.getField("battlePass").get(null);
        if (battlePass == null) return;
        Object data = battlePass.getClass().getMethod("getPlayerPass", ServerPlayerEntity.class).invoke(battlePass, player);
        if (data == null) return;
        int level = number(data.getClass().getMethod("getLevel").invoke(data));
        int xp = number(data.getClass().getMethod("getXP").invoke(data));
        PassState previous = pass.put(player.getUuid(), new PassState(level, xp));
        if (previous != null && (level > previous.level() || (level == previous.level() && xp > previous.xp()))) {
            engine.signal(player, "battlepass_progress");
        }
    }

    private void pollCalendar(ServerPlayerEntity player) throws Exception {
        if (!FabricLoader.getInstance().isModLoaded("cobblecalendar")) return;
        Class<?> root = Class.forName("com.kingpixel.cobblecalendar.CobbleCalendar");
        Object info = null;
        Object mapObject = root.getField("userInfoMap").get(null);
        if (mapObject instanceof Map<?, ?> map) info = map.get(player.getUuid());
        if (info == null) {
            Class<?> factory = Class.forName("com.kingpixel.cobblecalendar.database.DatabaseClientFactory");
            Object db = factory.getField("databaseClient").get(null);
            if (db != null) info = db.getClass().getMethod("getUserInfo", ServerPlayerEntity.class).invoke(db, player);
        }
        if (info == null) return;
        long claimed = ((Number) info.getClass().getMethod("getDayClaimed").invoke(info)).longValue();
        Long previous = calendar.put(player.getUuid(), claimed);
        if (previous != null && claimed > previous) engine.signal(player, "daily_claim");
    }

    private static int number(Object value) { return value instanceof Number n ? n.intValue() : 0; }
}
